(ns distributed.raft.main
  (:require [clojure.set :as set]
            [clojure.string :as str]
            )

  (:import (raft )))


(def me "127.0.0.1:8000")
(def servers #{"127.0.0.1:8000", "127.0.0.1:8001", "127.0.0.1:8002", "127.0.0.1:8002"})


(def global-state
  )




(defn send-append-request
  [client append-request]
  (.appendEntries client append-request))


#_(def s (server 8000))
#_(.shutdown s)
#_(-> (client "localhost" 8000)
      (send-append-request (build-append-request {:term 2 :leader-id "leader" :leader-commit 3})))


(defn start-server
  [config]
  (let [global-state (atom {:current-state nil
                            :current-term 0
                            :voted-for nil
                            :received-heartbeat false
                            :heartbeat-timer nil
                            :commit-index 0
                            :last-log-index 0
                            :last-log-term 0})
        follower (->Follower global-state config)]
    (server (:port config))))
