(ns distributed.raft.demo
  (:require [clojure.string :as str]
            [distributed.raft.main :as main]
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

(defn- kv-apply-fn [store]
  (fn [command]
    (let [[op kv] (str/split (str command) #"\s+" 2)]
      (when (= "SET" op)
        (let [[k v] (str/split kv #"=" 2)]
          (swap! store assoc k v))))
    command))

(defn- print-kv [nodes stores label]
  (println label)
  (doseq [[node store] (map vector nodes stores)]
    (println "  " (:me (:config node)) "kv=" (pr-str @store))))

(defn- snapshot [nodes label]
  (println label)
  (doseq [node nodes]
    (println "  " (:me (:config node))
             "state=" (some-> (:current-state @(:global-state node)) proto/state)
             "term=" (:current-term @(:global-state node))
             "log-len=" (count (:log @(:global-state node))))))

(defn -main [& _]
  (let [ports [8000 8001 8002]
        stores (mapv (fn [_] (atom {})) ports)
        configs (mapv (fn [port store]
                        (assoc (main/node-config port) :apply-fn (kv-apply-fn store)))
                      ports stores)
        nodes (mapv main/start-node! configs)]
    (println "Booted 3 nodes (8000, 8001, 8002).")
    (doseq [node nodes]
      (println "  " (:me (:config node))
               "loaded term=" (:current-term @(:global-state node))
               "log-len=" (count (:log @(:global-state node)))))
    (println "Waiting for election + recovery (no client commands yet)...")
    (loop [i 0]
      (snapshot nodes (str "  [" i "]"))
      (let [ldr (leader-node nodes)
            tgt-term (when ldr (:current-term @(:global-state ldr)))
            tgt-log (when ldr (count (:log @(:global-state ldr))))
            caught-up? (and ldr
                            (every? #(and (>= (:current-term @(:global-state %)) tgt-term)
                                          (>= (count (:log @(:global-state %))) tgt-log))
                                    nodes))]
        (if (or caught-up? (>= i 20))
          (println "Recovery complete." (when ldr (str " leader=" (:me (:config ldr)))))
          (do (Thread/sleep 250) (recur (inc i))))))
    (try
      (let [leader (leader-node nodes)]
        (println "Initial leader:" (when leader (:me (:config leader))))
        (doseq [cmd ["SET k1=1" "SET k2=2" "SET k3=3"]]
          (let [resp (client/submit-command "127.0.0.1:8000" cmd)]
            (println "  submit" cmd "=>" (:success resp) (:result resp) "leader" (:leader-id resp))))
        (Thread/sleep 600)
        (print-kv nodes stores "KV state after replication:"))

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
            (let [resp (client/submit-command (:me (:config new-leader)) "SET k4=4")]
              (println "submit SET k4=4 via" (:me (:config new-leader)) "=>" (:success resp) (:result resp) "leader" (:leader-id resp)))
            (println "WARNING: no new leader elected")))
        (Thread/sleep 600)
        (print-kv nodes stores "KV state after failover:"))

      (finally
        (println "Shutting down...")
        (doseq [node nodes]
          (try (main/stop-node! node) (catch Exception _)))
        (println "Done.")))))

(comment
  (-main))
