(ns distributed.swim.protocols)

(defprotocol Peer
  (get-id [_])
  (get-state [_])
  (send-message-with-ack [_ message])
  (send-message [_ message])
  (connect [_ other]))

(defprotocol Membership
  (update-state [_ peer state])
  (get-state [_ peer])
  (random-peer [_])
  (random-peers [_ n])
  (size [_])
  (add-peer [_ peer])
  (get-members [_]))

(defprotocol FailureDetector
  (ping-random-peer [_ membership])
  (indirect-probe-peer [_ p])
  (declare-dead-peer [_ p])
  (disseminate-peer-state [_ p state]))

(defprotocol AckService
  (send-ping [_ message ack-received-handler ack-timeout-handler])
  (send-indirect-ping [_ message ack-received-handler]))


