(ns distributed.swim.demo
  "Runnable SWIM demo. Three in-process nodes join a group; B stops
   answering for a while so A and C suspect it, then recovers and
   rejuvenates by bumping its incarnation; C is then killed and confirmed
   failed.

   Run with: lein run -m distributed.swim.demo

   All method-defining namespaces (membership, failure-detector,
   dissemination) are required here so every handle-message/apply-update
   method registers before the first message arrives."
  (:require [distributed.swim.state :as state]
            [distributed.swim.dispatch]
            [distributed.swim.membership :as membership]
            [distributed.swim.failure-detector :as fd]
            [distributed.swim.dissemination]))

(def ^:private ports {:A 9000 :B 9001 :C 9002})
(def ^:private ids (into {} (map (fn [[k p]] [k (state/node-id "127.0.0.1" p)]) ports)))

;; Suspicion timeout (8s) must far exceed the time to first suspicion (~2.5s
;; worst case with round-robin) so B is suspected but never confirmed failed
;; before it recovers. lambda 6 raises the gossip cap (lambda*log2 N ~ 10 for
;; N=3) so the SUSPECT update stays in A/C's buffers until after B comes back
;; and can self-heal on it.
(def ^:private demo-config {:suspicion-timeout-ms 8000 :lambda 6})

(defn- start! [port]
  (fd/start-node! (state/node-config port :config demo-config)))

(defn- view [node]
  (->> (:membership @node)
       vals
       (sort-by :port)
       (mapv (fn [{:keys [id status incarnation]}]
               {:id id :status status :incarnation incarnation}))))

(defn- print-view [label node]
  (println (str label ":") (pr-str (view node))))

(defn- wait-until
  "Poll `pred` every 100ms until it returns truthy or `timeout-ms` elapses."
  [timeout-ms pred]
  (loop [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (if (pred)
      true
      (if (>= (System/currentTimeMillis) deadline)
        false
        (do (Thread/sleep 100) (recur deadline))))))

(defn- status [node label] (membership/member-status node (ids label)))
(defn- incarnation [node label] (get-in @node [:membership (ids label) :incarnation]))

(defn- run-demo [A B C]
  (println "Started A (9000), B (9001), C (9002).")
  (let [b-joined (membership/join! B (ids :A))
        c-joined (membership/join! C (ids :A))]
    (println "B joins via A:" b-joined "| C joins via A:" c-joined)
    (Thread/sleep 1500)
    (println "\nMembership after join:")
    (print-view "A" A)
    (print-view "B" B)
    (print-view "C" C)

    (println "\n--- B drops all inbound traffic; A and C should suspect it ---")
    (swap! B assoc :drop-inbound? true)
    (let [suspected? (wait-until 6000
                                 #(and (= :suspected (status A :B))
                                       (= :suspected (status C :B))))]
      (println "A sees B:" (status A :B) "| C sees B:" (status C :B))
      (swap! B assoc :drop-inbound? false)
      (println "B back online; waiting for it to rejuvenate (incarnation bump)...")
      (let [revived? (wait-until 8000
                                 #(and (= :alive (status A :B))
                                       (= :alive (status C :B))
                                       (>= (incarnation A :B) 1)
                                       (>= (incarnation C :B) 1)))]
        (println "B incarnation at A:" (incarnation A :B)
                 "| at C:" (incarnation C :B))
        (print-view "A" A)
        (print-view "B" B)
        (print-view "C" C)

        (println "\n--- Killing C ---")
        (fd/stop-node! C)
        (let [failed? (wait-until 15000
                                  #(and (nil? (status A :C))
                                        (nil? (status B :C))))]
          (println "Final views (C removed):")
          (print-view "A" A)
          (print-view "B" B)
          (and b-joined c-joined suspected? revived? failed?))))))

(defn -main [& _]
  (let [A (start! (:A ports))
        B (start! (:B ports))
        C (start! (:C ports))
        ok? (try
              (run-demo A B C)
              (catch Exception e
                (println "demo error:" (.getMessage e))
                false))]
    (fd/stop-node! A)
    (fd/stop-node! B)
    (fd/stop-node! C)
    (shutdown-agents)
    (println (if ok? "\nSWIM demo: PASS" "\nSWIM demo: FAIL"))
    (System/exit (if ok? 0 1))))
