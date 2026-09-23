(ns distributed.raft.leader
  (:require [clojure.string :as str]
            [distributed.raft.protocol :as proto :refer [RaftState]]
            [distributed.raft.rpc :as rpc]
            [distributed.raft.state :as state]))

(defn- peers [config] (disj (:servers config) (:me config)))

(defn- send-append-to-peer! [global-state config peer]
  (try
    (let [{:keys [current-term log commit-index leader-volatile]} @global-state
          next-index (get-in leader-volatile [:next-index peer] 1)
          prev-log-index (dec next-index)
          prev-log-term (or (state/log-entry-term log prev-log-index) 0)
          entries (vec (subvec log (dec next-index)))
          [host port] (str/split peer #":")
          resp (-> (rpc/client host (Integer/parseInt port))
                   (.appendEntriesRPC
                    (rpc/build-append-request
                     {:term current-term
                      :leader-id (:me config)
                      :prev-log-index prev-log-index
                      :prev-log-term prev-log-term
                      :entries entries
                      :leader-commit commit-index}))
                   (proto/->clj))]
      ;; Capture what we actually SENT so the response can be applied only
      ;; while those values still match the leader's volatile state (otherwise
      ;; out-of-order responses would corrupt matchIndex/nextIndex).
      {:peer peer :resp resp :entry-count (count entries)
       :sent-next-index next-index :sent-term current-term})
    (catch Exception e
      {:peer peer :error (.getMessage e)})))

(defn- handle-peer-result! [global-state config result]
  (let [resp (:resp result)]
    (cond
      (:error result)
      nil

      (nil? resp)
      nil

      (state/step-down-on-term! global-state config (:term resp) >)
      nil

      (:success resp)
      (state/with-state-lock global-state
        (when (= :leader (proto/state (:current-state @global-state)))
          (let [peer (:peer result)
                sent-term (:sent-term result)
                sent-next-index (:sent-next-index result)]
            (when (and (= sent-term (:current-term @global-state))
                       (= sent-next-index (get-in @global-state [:leader-volatile :next-index peer])))
              (let [new-match (+ sent-next-index (:entry-count result) -1)]
                (swap! global-state assoc-in [:leader-volatile :match-index peer] new-match)
                (swap! global-state assoc-in [:leader-volatile :next-index peer] (inc new-match))
                (state/advance-commit-index! global-state))))))

      :else
      (state/with-state-lock global-state
        (when (= :leader (proto/state (:current-state @global-state)))
          (let [peer (:peer result)
                sent-term (:sent-term result)
                sent-next-index (:sent-next-index result)]
            (when (and (= sent-term (:current-term @global-state))
                       (= sent-next-index (get-in @global-state [:leader-volatile :next-index peer])))
              (swap! global-state assoc-in [:leader-volatile :next-index peer] (max 1 (dec sent-next-index))))))))))

(defn replicate-round!
  "Send AppendEntries to every peer. Public so state/start-heartbeat! can reach
   it through requiring-resolve without a require cycle."
  [global-state config]
  (when (= :leader (proto/state (:current-state @global-state)))
    (let [executor (:rpc-executor @global-state)
          others (peers config)]
      (doseq [peer others]
        (.execute executor
                  (fn [] (handle-peer-result! global-state config
                                              (send-append-to-peer! global-state config peer))))))))

(defrecord Leader [global-state config]
  RaftState
  (state [_] :leader)

  (init [_]
    (state/with-state-lock global-state
      (let [others (peers config)
            nxt (inc (count (:log @global-state)))
            next-index (zipmap others (repeat nxt))
            match-index (zipmap others (repeat 0))]
        (swap! global-state assoc
               :leader-id (:me config)
               :leader-volatile {:next-index next-index :match-index match-index}
               :votes #{})
        ;; Append a no-op entry for the current term so that a newly elected
        ;; leader can commit entries from previous terms (and learn which
        ;; entries are committed).
        (swap! global-state update :log conj {:term (:current-term @global-state) :command nil})
        ;; Advance commitment so a single-node cluster commits its own no-op
        ;; (and, later, its own entries) immediately.
        (state/advance-commit-index! global-state)
        (state/start-heartbeat! global-state config)
        (.execute (:rpc-executor @global-state)
                  (fn [] (replicate-round! global-state config))))))

  (handle-append-entries [_ request respond-to]
    (let [{:keys [term]} request]
      (if (state/step-down-on-term! global-state config term >)
        (proto/handle-append-entries (:current-state @global-state) request respond-to)
        (rpc/send-grpc-response respond-to
                                (rpc/build-append-response {:term (:current-term @global-state)
                                                             :success false})))))

  (handle-vote-request [_ request respond-to]
    (let [{:keys [term]} request]
      (if (state/step-down-on-term! global-state config term >)
        (proto/handle-vote-request (:current-state @global-state) request respond-to)
        (rpc/send-grpc-response respond-to
                                (rpc/build-vote-response {:term (:current-term @global-state)
                                                           :vote-granted false})))))

  (handle-submit-command [_ request respond-to]
    (let [command (:command request)
          p (promise)
          is-leader? (state/with-state-lock global-state
                       (when (= :leader (proto/state (:current-state @global-state)))
                         (let [entry {:term (:current-term @global-state) :command command}
                               idx (inc (count (:log @global-state)))]
                           (swap! global-state update :log conj entry)
                           (swap! global-state assoc-in [:pending idx] p)
                           (state/advance-commit-index! global-state)
                           true)))]
      (if is-leader?
        (do
          (.execute (:rpc-executor @global-state)
                    (fn [] (replicate-round! global-state config)))
          (.execute (:rpc-executor @global-state)
                    (fn []
                      (let [result (deref p 5000 ::timeout)]
                        (rpc/send-grpc-response
                         respond-to
                         (if (and (not= result ::timeout) (not= result :aborted))
                           (rpc/build-submit-response {:success true
                                                       :leader-id (:me config)
                                                       :committed true
                                                       :result (str result)})
                           (rpc/build-submit-response {:success false
                                                       :leader-id (or (:leader-id @global-state) (:me config))
                                                       :committed false
                                                       :result ""})))))))
        (rpc/send-grpc-response respond-to
                                (rpc/build-submit-response {:success false
                                                            :leader-id (or (:leader-id @global-state) (:me config))
                                                            :committed false
                                                            :result ""}))))))
