(ns distributed.raft.rpc
  (:require [distributed.raft.protocol :refer [ToClojure ->clj handle-append-entries handle-vote-request]])
  (:import (raft RaftGrpc$RaftImplBase  
                 RaftSpec$AppendEntries 
                 RaftSpec$AppendEntriesResponse
                 RaftSpec$RequestVote 
                 RaftSpec$RequestVoteResponse
                 RaftGrpc)
           (io.grpc Server ServerBuilder ManagedChannelBuilder)))


(defn build-append-request
  [id entries {:keys [current-term last-log-term commit-index]}]
  (-> (RaftSpec$AppendEntries/newBuilder)
    (.setTerm current-term)
    (.setLeaderId id)
    (.setPrevLogTerm last-log-term)
    (.addAllEntries entries)
    (.setLeaderCommit commit-index)
    .build))

(defn build-append-response
  [{:keys [term success]}]
  (-> (RaftSpec$AppendEntriesResponse/newBuilder)
      (.setTerm term)
      (.setSuccess success)
      .build))

(defn build-vote-request
  [id {:keys [current-term last-log-index last-log-term]}]
  (-> (RaftSpec$RequestVote/newBuilder)
      (.setTerm (inc current-term))
      (.setCandidateId id)
      (.setLastLogIndex last-log-index)
      (.setLastLogTerm last-log-term)
      .build))

(defn send-grpc-response
  [respond-to response]
  (.onNext respond-to response) 
  (.onCompleted respond-to))

(extend-protocol ToClojure
  RaftSpec$AppendEntries
  (->clj 
    [this]
    {:term (.getTerm this)
     :leader-id (.getLeaderId this)
     :prev-log-term (.getPrevLogTerm this)
     :prev-log-index (.getPrevLogIndex this)
     :entries (.getAllEntries this)
     :leader-commit (.getLeaderCommit this)})

  RaftSpec$RequestVote
  (->clj 
    [this]
    {:term (.getTerm this)
     :candidate-id (.getCandidateId this)
     :last-log-index (.getLastLogIndex this)
     :last-log-term (.getLastLogTerm this)})

  RaftSpec$RequestVoteResponse
  (->clj
    [this]
    {:term (.getTerm this)
     :vote-granted (.getVoteGranted this)}))

(defn service [global-state]
  (proxy [RaftGrpc$RaftImplBase] []
    (appendEntriesRPC
      [^RaftSpec$AppendEntries request response]
      (println request)
      (let [current-state (:current-state @global-state)
          request (->clj request)]
        (if current-state
          (handle-append-entries current-state (->clj request) response)
          (println "Unable to handle " request " currently in an uninitialized state"))))
    (requestVoteRPC
      [^RaftSpec$AppendEntries request response]
      (println request)
     (let [current-state (:current-state @global-state)]
        (if current-state
          (handle-vote-request current-state (->clj request) response)
          (println "Unable to handle " request " currently in an uninitialized state"))))))

(defn server
  [port global-state]
  (let [builder (ServerBuilder/forPort port)
        service (service global-state)
        _       (.addService builder service)
        server  (.build builder)]
    (.start server)))

(defn client
  [host port]
  (RaftGrpc/newBlockingStub
   (-> (ManagedChannelBuilder/forAddress host port)
       (.usePlaintext true)
       .build)))


