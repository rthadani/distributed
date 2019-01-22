(ns distributed.swim.membership
    (:require [distributed.swim.protocols :refer [Membership] :as p]))

(def peer-state [:suspect :healthy :dead] )

(defrecord SwimMembership [peers]
  Membership
  (update-state 
    [_ peer state]
    (swap! peers assoc (p/get-id peer) (p/get-state peer)))
  (get-state 
    [_ peer]
    (.get-state (get @peers (p/get-id peer))))
  (random-peer
    [_ peer]
    (as-> (keys @peers) $
      (rand-nth $)
      (get @peers $)))
  (random-peers 
    [_ n]
    (->> (keys @peers)
         shuffle
         (reduce (fn [[keys i] key] (get @peers key)) [[] 0])
         first))
  (get-members [_]
         (let [all @peers]
           (map (fn [peer] {:id (p/get-id peer) :state (p/get-state peer)}) all))))



;;TODO deserialize a set of peers to bootstrap 


