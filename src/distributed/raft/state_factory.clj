(ns distributed.raft.state-factory)

(declare ->Follower)
(declare ->Candidate)

(defn make-follower
  [global-state config]
  (->Follower {:global-state global-state :config config}))

(defn make-candidate
  [global-state config]
  (->Candidate {:global-state global-state :config config}))

#_(defn make-leader
  [global-state config]
  (->Leader {:global-state global-state :config config}))