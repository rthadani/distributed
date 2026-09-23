(ns distributed.raft.state
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [distributed.raft.protocol :as proto])
  (:import (java.nio.file AtomicMoveNotSupportedException CopyOption Files StandardCopyOption)
           (java.util.concurrent Executors TimeUnit)))

;;; Leaf namespace: owns the per-node state atom, log helpers, timers, and
;;; state transitions. Role namespaces (follower/candidate/leader) require this
;;; namespace, and this namespace reaches back to them lazily via
;;; `requiring-resolve` inside `change-state`/`start-heartbeat!` to avoid a
;;; require cycle.

(defmacro with-state-lock [global-state & body]
  `(locking (:lock @~global-state) ~@body))

(def persistent-keys [:current-term :voted-for :log])

(defn load-state!
  "Reads the 3 persistent fields from `state-file`. Returns defaults when the
   path is nil, absent, or unreadable (corrupt files fail safe to a fresh log)."
  [state-file]
  (let [defaults {:current-term 0 :voted-for nil :log []}]
    (if (and state-file (.isFile (io/file state-file)))
      (try
        (let [m (edn/read-string (slurp state-file))]
          (merge defaults (select-keys m persistent-keys)))
        (catch Exception e
          (println "WARNING: could not read state file" state-file
                   "(" (.getMessage e) "); starting fresh")
          defaults))
      defaults)))

(defn persist-state!
  "Atomically writes the 3 persistent fields to `state-file` as EDN."
  [state-file state]
  (let [f (io/file state-file)
        tmp (io/file (str state-file ".tmp"))
        snapshot (pr-str (select-keys state persistent-keys))]
    (some-> f .getParentFile .mkdirs)
    (spit tmp snapshot)
    (try
      (Files/move (.toPath tmp) (.toPath f)
                  (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                          StandardCopyOption/REPLACE_EXISTING]))
      (catch AtomicMoveNotSupportedException _
        (Files/move (.toPath tmp) (.toPath f)
                    (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING]))))))

(defn new-node-state
  [config]
  (let [state-file (:state-file config)
        scheduler (Executors/newScheduledThreadPool 1)
        rpc-executor (Executors/newCachedThreadPool)
        a (atom (merge {:current-state nil
                        :current-term 0
                        :voted-for nil
                        :log []
                        :commit-index 0
                        :last-applied 0
                        :applied []
                        :leader-id nil
                        :leader-volatile nil
                        :pending {}
                        :votes #{}
                        :election-timer nil
                        :heartbeat-timer nil
                        :lock (Object.)
                        :scheduler scheduler
                        :rpc-executor rpc-executor}
                       (load-state! state-file)))]
    (when state-file
      (add-watch a ::persist
                 (fn [_ _ old new]
                   (when (not= (select-keys old persistent-keys)
                               (select-keys new persistent-keys))
                     (try
                       (persist-state! state-file new)
                       (catch Exception e
                         (println "WARNING: failed to persist state to" state-file
                                  "(" (.getMessage e) ")")))))))
    a))

;;; Pure log helpers (1-based log indices; index i lives at (nth log (dec i))).

(defn log-last-index [log] (count log))

(defn log-last-term [log] (:term (peek log) 0))

(defn log-entry-term
  [log index]
  (when (<= 1 index (count log))
    (:term (nth log (dec index)))))

(defn log-up-to-date?
  "True when a candidate's log is at least as up-to-date as `log`."
  [log cand-last-log-index cand-last-log-term]
  (let [local-last-term (log-last-term log)
        local-last-index (log-last-index log)]
    (or (> cand-last-log-term local-last-term)
        (and (= cand-last-log-term local-last-term)
             (>= cand-last-log-index local-last-index)))))

(defn append-entries-to-log
  "Applies an AppendEntries message to `log`. Returns the new log, or nil when
   the prev-log consistency check fails (prev-log-index/term do not match).

   Entries are only dropped when they CONFLICT with an incoming entry (same
   index, different term). Matching existing entries and any follower entries
   past the incoming range are retained; a heartbeat (empty entries) never
   truncates the log."
  [log prev-log-index prev-log-term entries]
  (let [n (count log)]
    (cond
      (and (pos? prev-log-index)
           (or (> prev-log-index n)
               (not= (log-entry-term log prev-log-index) prev-log-term)))
      nil

      (empty? entries)
      log

      :else
      (loop [offset 0]
        (let [idx (+ prev-log-index offset 1)
              incoming (nth entries offset nil)]
          (if (nil? incoming)
            ;; All incoming entries matched existing terms; keep the log
            ;; unchanged, including any extra follower entries past the
            ;; incoming range.
            log
            (let [existing (nth log (dec idx) nil)]
              (if (and existing (= (:term existing) (:term incoming)))
                (recur (inc offset))
                ;; Conflict (different term) or gap (past end): drop this entry
                ;; and everything after it, then append the remaining incoming
                ;; entries.
                (into (subvec (vec log) 0 (dec idx))
                      (drop offset entries))))))))))

(defn majority [n] (inc (quot n 2)))

(defn advance-commit-index
  "Returns the highest index N > commit-index such that a majority of
   match-indexes are >= N and log[N].term == current-term, else commit-index.
   This encodes Raft's rule that only entries from the current term may be
   committed by counting replicas."
  [commit-index current-term log match-indexes]
  (let [maj (majority (count match-indexes))
        max-n (count log)]
    (loop [n max-n]
      (if (<= n commit-index)
        commit-index
        (let [replicated (count (filter #(>= % n) (vals match-indexes)))
              term-at-n (:term (nth log (dec n)))]
          (if (and (>= replicated maj)
                   (= term-at-n current-term))
            n
            (recur (dec n))))))))

;;; Timers.

(defn- random-election-timeout-ms [base]
  (+ base (rand-int base)))

(defn cancel-election-timer! [global-state]
  (when-let [t (:election-timer @global-state)]
    (.cancel t false)
    (swap! global-state assoc :election-timer nil)))

(defn reset-election-timer!
  "Schedules the next one-shot election timeout. `timeout-fn` MUST be a
   zero-argument function (Clojure fns implement java.lang.Runnable, so it is
   passed directly to the scheduler)."
  [global-state config timeout-fn]
  (cancel-election-timer! global-state)
  (let [scheduler (:scheduler @global-state)
        base (:election-timeout-ms config)
        delay (random-election-timeout-ms base)
        fut (.schedule scheduler
                       ^Runnable timeout-fn
                       (long delay)
                       TimeUnit/MILLISECONDS)]
    (swap! global-state assoc :election-timer fut)))

(defn stop-heartbeat! [global-state]
  (when-let [t (:heartbeat-timer @global-state)]
    (.cancel t false)
    (swap! global-state assoc :heartbeat-timer nil)))

(defn start-heartbeat!
  [global-state config]
  (stop-heartbeat! global-state)
  (let [scheduler (:scheduler @global-state)
        interval (:heartbeat-ms config)
        task (fn []
               (when (= :leader (proto/state (:current-state @global-state)))
                 (let [replicate (requiring-resolve 'distributed.raft.leader/replicate-round!)]
                   (replicate global-state config))))
        fut (.scheduleAtFixedRate scheduler
                                  ^Runnable task
                                  (long interval)
                                  (long interval)
                                  TimeUnit/MILLISECONDS)]
    (swap! global-state assoc :heartbeat-timer fut)))

;;; State-machine application and pending-promise settlement.

(defn abort-pending! [global-state]
  (doseq [[_ p] (:pending @global-state)]
    (when-not (realized? p)
      (deliver p :aborted)))
  (swap! global-state assoc :pending {}))

(defn apply-committed!
  "Applies committed-but-unapplied log entries in order. No-op entries have a
   nil :command and are skipped (but still advance :last-applied and settle any
   pending promise for that index)."
  [global-state]
  (with-state-lock global-state
    (loop []
      (let [{:keys [commit-index last-applied log pending]} @global-state]
        (when (> commit-index last-applied)
          (let [next-idx (inc last-applied)
                entry (nth log (dec next-idx))
                command (:command entry)]
            (swap! global-state assoc :last-applied next-idx)
            (when command
              (swap! global-state update :applied conj command))
            (when-let [p (get pending next-idx)]
              (when-not (realized? p)
                (deliver p (or command "")))
              (swap! global-state update :pending dissoc next-idx))
            (recur)))))))

(defn advance-commit-index!
  "Leader-side commit advancement, followed by applying committed entries."
  [global-state]
  (with-state-lock global-state
    (let [{:keys [commit-index current-term log leader-volatile]} @global-state
          match-indexes (assoc (:match-index leader-volatile) :self (count log))
          new-ci (advance-commit-index commit-index current-term log match-indexes)]
      (when (> new-ci commit-index)
        (swap! global-state assoc :commit-index new-ci))
      (apply-committed! global-state))))

;;; State transitions.

(defn change-state
  [new-state global-state config]
  (let [make (case new-state
               :Follower (requiring-resolve 'distributed.raft.follower/->Follower)
               :Candidate (requiring-resolve 'distributed.raft.candidate/->Candidate)
               :Leader (requiring-resolve 'distributed.raft.leader/->Leader))
        record (make global-state config)]
    (with-state-lock global-state
      (swap! global-state assoc :current-state record)
      (proto/init record))
    record))

(defn step-down-to-follower!
  [global-state config]
  (with-state-lock global-state
    (when (not= :follower (proto/state (:current-state @global-state)))
      (cancel-election-timer! global-state)
      (stop-heartbeat! global-state)
      (abort-pending! global-state)
      (swap! global-state assoc
             :voted-for nil
             :leader-volatile nil
             :leader-id nil
             :votes #{})
      (change-state :Follower global-state config))))

(defn step-down-on-term!
  "If (pred term current-term) holds, records the higher term and becomes a
   Follower. Returns true when it stepped down."
  [global-state config term pred]
  (with-state-lock global-state
    (let [ct (:current-term @global-state)]
      (when (pred term ct)
        (when (> term ct)
          (swap! global-state assoc :current-term term))
        (step-down-to-follower! global-state config)
        true))))
