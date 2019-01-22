(ns distributed.swim.failure-detector
    (:require [distributed.swim.protocols :refer [FailureDetector] :as p]
              [distributed.swim.message :as m]))


(defrecord SwimFailureDetector [ack-service] 
  FailureDetector
  (ping-random-peer
   [this membership & [additional-data-fn]]
    (let [peer (p/random-peer membership)
          message (m/ping-ack-message :ping (p/get-id peer) (p/get-members membership))]
      (-> (connect peer)
          (p/send-message message 
                        (partial on-ack-timeout this membership) 
                        (partial on-ack-received this))))))

  (indirect-probe-peer [_ peer]
                       )
  (declare-dead-peer [_ peer])
  (disseminate-peer-state [_ peer state])       
           )

(defn on-ack-timeout
  [failure-detector membership id]
  )

(defn on-ack-received
  [failure-detector membership message])


(defn on-message-sent
  [failure-detector membership id])