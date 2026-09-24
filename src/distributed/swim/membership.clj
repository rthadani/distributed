(ns distributed.swim.membership
  "Membership list operations, round-robin probe target selection, and the
   JOIN handshake."
  (:require [distributed.swim.dispatch :refer [handle-message apply-update]]
            [distributed.swim.message :as message]
            [distributed.swim.state :as state]
            [distributed.swim.network-peer :as net]))

(defn upsert-member!
  "Add a member if absent (alive, incarnation 0)."
  [node {:keys [id host port]}]
  (swap! node update :membership
         (fn [m]
           (if (contains? m id)
             m
             (assoc m id {:id id :host host :port port
                          :incarnation 0 :status :alive})))))

(defn remove-member! [node id]
  (swap! node update :membership dissoc id))

(defn member-status [node id]
  (get-in @node [:membership id :status]))

(defn live-peers
  "Alive or suspected peers, excluding self."
  [node]
  (let [{:keys [id membership]} @node]
    (->> membership vals
         (remove #(= id (:id %)))
         (filter #(#{:alive :suspected} (:status %))))))

(defn random-peers
  "Up to n uniformly random live peers."
  [node n]
  (->> (live-peers node) shuffle (take n) vec))

(defn round-robin-target
  "The next probe target: cycle through live peers in a shuffled order,
   reshuffling after each full traversal and whenever membership changes.
   This gives time-bounded completeness (each member is probed once per
   traversal) while keeping random selection's distribution."
  [node]
  (let [{:keys [id membership]} @node
        others (->> membership vals (remove #(= id (:id %))) (mapv :id))]
    (when (seq others)
      (when-not (= (set others) (set (:probe-order @node)))
        (swap! node assoc :probe-order (shuffle others) :probe-index 0))
      (let [{:keys [probe-order probe-index]} @node
            target (nth probe-order probe-index)
            next-idx (mod (inc probe-index) (count probe-order))]
        (swap! node assoc :probe-index next-idx)
        (when (zero? next-idx)
          (swap! node assoc :probe-order (shuffle others)))
        (get membership target)))))

;;; JOIN: server side adds the joiner and seeds its membership list.

(defmethod handle-message :join [node msg]
  (let [joiner-id (:sender-id msg)
        {:keys [host port]} (state/parse-id joiner-id)]
    (when (and host port)
      (upsert-member! node {:id joiner-id :host host :port port})
      (state/enqueue! node (message/update-entry joiner-id 0 :alive))
      (let [seeds (mapv (fn [[mid m]] (message/update-entry mid (:incarnation m) :alive))
                        (:membership @node))]
        (message/msg :ack
                     :sender-id (:id @node)
                     :target-id joiner-id
                     :updates seeds)))))

;;; JOIN: client side.

(defn join!
  "Join `node` to the group via `contact-id` (host:port), seeding membership
   from the contact's reply. Returns true on success."
  [node contact-id]
  (let [{:keys [id]} @node
        {:keys [host port]} (state/parse-id contact-id)]
    (when (and host port)
      (try
        (let [resp (net/send! host port
                              (message/msg :join
                                           :sender-id id
                                           :target-id contact-id)
                              2000)]
          (when (= :ack (:type resp))
            (doseq [u (:updates resp)] (apply-update node u))
            true))
        (catch Exception _ false)))))
