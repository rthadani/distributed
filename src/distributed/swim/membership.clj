(ns distributed.swim.membership
    (:require [distributed.swim.protocols :refer [Membership]]))

(def peer-state [:suspect :healthy :dead] )

(defrecord SwimMembership [peers]
  Membership
  (update-state 
    [_ peer state]
    (swap! peers assoc (.get-id peer) (.get-state peer)))
  (get-state 
    [_ peer]
    (.get-state (get @peers (.get-id peer))))
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
     first)))



;;TODO deserialize a set of peers to bootstrap 


