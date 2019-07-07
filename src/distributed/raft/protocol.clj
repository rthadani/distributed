(ns distributed.raft.protocol)

(defprotocol RaftState
  (state [this])
  (init [this])
  (handle-append-entries [this request respond-to])
  (handle-vote-request [this request respond-to]))

(defprotocol ToClojure
  (->clj [this]))
