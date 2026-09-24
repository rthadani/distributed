(ns distributed.swim.dissemination
  "Infection-style dissemination and the suspicion/incarnation subprotocol.

  apply-update implements the incarnation preference order from the paper
  (section 4.2): ALIVE and SUSPECT apply only when their incarnation is at
  least the locally known one, and CONFIRM is unconditional."
  (:require [distributed.swim.dispatch :refer [handle-message apply-update
                                               apply-updates!]]
            [distributed.swim.message :as message]
            [distributed.swim.state :as state]
            [distributed.swim.membership :as membership]
            [distributed.swim.network-peer :as net]))

(defn gossip!
  "Enqueue `update` for piggybacking and fire-and-forget it to up to k live
   peers, so a self-rejuvenating member spreads ALIVE immediately."
  [node {:keys [member-id incarnation type] :as update}]
  (state/enqueue! node update)
  (let [sender-id (:id @node)
        peers (membership/random-peers node (:k (:config @node)))
        timeout (:ack-timeout-ms (:config @node))]
    (doseq [p peers]
      (future
        (try
          (net/send! (:host p) (:port p)
                     (message/msg type
                                  :sender-id sender-id
                                  :target-id member-id
                                  :incarnation incarnation)
                     timeout)
          (catch Exception _))))))

;;; apply-update: merge one piggybacked update into the membership view.
;;; The incarnation comparison and timer live in membership.clj; here we just
;;; apply the canonical transition and re-gossip the result.

(defmethod apply-update :alive [node {:keys [member-id incarnation]}]
  (when (membership/mark-alive! node member-id incarnation)
    (state/enqueue! node (message/update-entry member-id incarnation :alive))))

(defmethod apply-update :suspect [node {:keys [member-id incarnation]}]
  (if (= member-id (:id @node))
    ;; Suspected in our current incarnation: bump and broadcast ALIVE.
    (when-let [new-inc (membership/self-heal! node member-id incarnation)]
      (gossip! node (message/update-entry member-id new-inc :alive)))
    (do
      (membership/mark-suspected! node member-id incarnation)
      (state/enqueue! node (message/update-entry member-id incarnation :suspect)))))

(defmethod apply-update :confirm [node {:keys [member-id incarnation]}]
  (when (membership/mark-failed! node member-id)
    (state/enqueue! node (message/update-entry member-id incarnation :confirm))))

;;; handle-message: apply the payload, re-enqueue for onward spread, and ACK.

(defmethod handle-message :suspect [node msg]
  (apply-updates! node (:updates msg))
  (let [u {:member-id (:target-id msg) :incarnation (:incarnation msg) :type :suspect}]
    (apply-update node u)
    (state/enqueue! node u))
  (message/msg :ack :sender-id (:id @node) :updates (state/pick-piggyback node)))

(defmethod handle-message :alive [node msg]
  (apply-updates! node (:updates msg))
  (let [u {:member-id (:target-id msg) :incarnation (:incarnation msg) :type :alive}]
    (apply-update node u)
    (state/enqueue! node u))
  (message/msg :ack :sender-id (:id @node) :updates (state/pick-piggyback node)))

(defmethod handle-message :confirm [node msg]
  (apply-updates! node (:updates msg))
  (let [u {:member-id (:target-id msg) :incarnation (:incarnation msg) :type :confirm}]
    (apply-update node u)
    (state/enqueue! node u))
  (message/msg :ack :sender-id (:id @node) :updates (state/pick-piggyback node)))
