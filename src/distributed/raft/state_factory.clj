(ns distributed.raft.state-factory
  (:require [distributed.raft.protocol :as r]))

(declare ->Follower)
(declare ->Candidate)
(declare ->Leader)

(defn make-follower
  [global-state config]
  (->Follower {:global-state global-state :config config}))

(defn make-candidate
  [global-state config]
  (->Candidate {:global-state global-state :config config}))

(defn make-leader
  [global-state config]
  (->Leader {:global-state global-state :config config}))

(defn change-state [new-state global-state config]
  (let [new-state (case new-state
                    :Follower (make-follower global-state config)
                    :Candidate (make-candidate global-state config) 
                    :Leader (make-leader global-state config))]
    (r/init new-state)
    (swap! global-state assoc :current-state new-state)))
