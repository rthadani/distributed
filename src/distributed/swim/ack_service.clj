(ns distributed.swim.ack-service
  (:require []))

(defrecord SwimAckService [membership]
  AckService
  (send-ping [_ message ack-received-handler ack-timeout-handler]
             ))