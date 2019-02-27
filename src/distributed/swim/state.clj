(ns distributed.swim.state)

(declare index-of remove-peer)

(def peer-state
  (ref {:peers  []
         :pending-acks []
         :pending-indirect-acks []
         :type nil }))

(defn bootstrap
  [peers type]
  (dosync
    (alter peer-state assoc :peers (map #({:name % :state :healthy :type type}) peers)))
  (:peers @peer-state))

(defn remove-peer
  [name]
  (dosync
    (when-let [i (index-of #(= :name %) (:peers @peer-state))]
      (alter peer-state update :peers #(remove-peer % i)))))

(defn update-peer
  [name state]
  (dosync
    (remove-peer name)
    (alter peer-state update :peers conj {:name name :state state})))

(defn- remove-peer-index
  [peer-list index]
  (concat (subvec peer-list 0 index)
          (subvec (inc index) (count peer-list))))

(defn random-healthy-peer
  []
  (dosync
    (-> (filter #(= (:state %) :healthy) (:peers @peer-state))
        (rand-nth))))

(defn random-k-healthy-peers
  )

