(ns distributed.raft)

(defprotocol IState
  "Persistent state on all servers"
  (current-term[state])
  (voted-for [state])
  (log [state])
  (commit-index [state])
  )
