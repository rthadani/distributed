(ns distributed.raft.demo
  (:require [distributed.raft.main :as main]
            [distributed.raft.client :as client]
            [distributed.raft.protocol :as proto]))

(defn- leader-node [nodes]
  (some (fn [node]
          (when (= :leader (proto/state (:current-state @(:global-state node))))
            node))
        nodes))

(defn- wait-until [timeout-ms f]
  (loop [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (let [v (f)]
      (if (or v (>= (System/currentTimeMillis) deadline))
        v
        (do (Thread/sleep 50) (recur deadline))))))

(defn- print-applied [nodes label]
  (println label)
  (doseq [node nodes]
    (println "  " (:me (:config node))
             "applied=" (vec (:applied @(:global-state node))))))

(defn -main [& _]
  (let [configs (mapv main/node-config [8000 8001 8002])
        nodes (mapv main/start-node! configs)]
    (println "Booted 3 nodes (8000, 8001, 8002).")
    (doseq [node nodes]
      (println "  " (:me (:config node))
               "loaded term=" (:current-term @(:global-state node))
               "log-len=" (count (:log @(:global-state node)))))
    (println "Waiting for election...")
    (Thread/sleep 1500)
    (try
      (let [leader (leader-node nodes)]
        (println "Initial leader:" (when leader (:me (:config leader))))
        (doseq [cmd ["SET k=1" "SET k=2" "SET k=3"]]
          (let [resp (client/submit-command "127.0.0.1:8000" cmd)]
            (println "  submit" cmd "=>" (:success resp) (:result resp) "leader" (:leader-id resp))))
        (Thread/sleep 600)
        (print-applied nodes "Applied state after replication:"))

      (let [leader (leader-node nodes)]
        (if leader
          (let [killed-port (:port (:config leader))]
            (println "Killing leader on port" killed-port)
            (main/stop-node! leader))
          (println "WARNING: no leader to kill"))
        (println "Waiting for re-election...")
        (Thread/sleep 2500)
        (let [killed-port (when leader (:port (:config leader)))
              survivors (remove #(= (:port (:config %)) killed-port) nodes)
              new-leader (wait-until 8000 #(leader-node survivors))]
          (if new-leader
            (let [resp (client/submit-command (:me (:config new-leader)) "SET k=4")]
              (println "submit SET k=4 via" (:me (:config new-leader)) "=>" (:success resp) (:result resp) "leader" (:leader-id resp)))
            (println "WARNING: no new leader elected")))
        (Thread/sleep 600)
        (print-applied nodes "Applied state after failover:"))

      (finally
        (println "Shutting down...")
        (doseq [node nodes]
          (try (main/stop-node! node) (catch Exception _)))
        (println "Done.")))))

(comment
  (-main))
