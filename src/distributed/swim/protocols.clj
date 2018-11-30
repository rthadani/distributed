(ns distributed.swim.protocols)

(defprotocol Peer
  (get-id [_])
  (get-state [_])
  (send-message [_ other message])
  (receive-message [_ other])
  (connect [_ other]))

(defprotocol Membership
  (update-state [_ peer state])
  (get-state [_ peer])
  (random-peer [_])
  (random-peers [_ n])
  (size [_])
  (add-peer [_ peer]))

(defprotocol FailureDetector
  (ping-random-peer [_ membership])
  (indirect-probe-peer [_ p])
  (declare-dead-peer [_ p])
  (disseminate-peer-state [_ p state]))


