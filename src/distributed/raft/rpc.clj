(ns distributed.raft.rpc
  (:require [distributed.raft.protocol :refer [ToClojure ->clj
                                               handle-append-entries
                                               handle-vote-request
                                               handle-submit-command]])
  (:import (raft RaftGrpc$RaftImplBase
                 RaftGrpc
                 RaftSpec$AppendEntries
                 RaftSpec$AppendEntriesResponse
                 RaftSpec$LogEntry
                 RaftSpec$RequestVote
                 RaftSpec$RequestVoteResponse
                 RaftSpec$SubmitCommand
                 RaftSpec$SubmitCommandResponse)
           (io.grpc ServerBuilder ManagedChannelBuilder)
           (io.grpc.stub StreamObserver)))

;;; Message builders.

(defn build-log-entry [{:keys [term command]}]
  (-> (RaftSpec$LogEntry/newBuilder)
      (.setTerm (long term))
      (.setCommand (str (or command "")))
      .build))

(defn build-append-request
  [{:keys [term leader-id prev-log-index prev-log-term entries leader-commit]}]
  (let [b (doto (RaftSpec$AppendEntries/newBuilder)
            (.setTerm (long term))
            (.setLeaderId (str leader-id))
            (.setPrevLogIndex (long prev-log-index))
            (.setPrevLogTerm (long prev-log-term))
            (.setLeaderCommit (long leader-commit)))]
    (doseq [e entries] (.addEntries b (build-log-entry e)))
    (.build b)))

(defn build-append-response [{:keys [term success]}]
  (-> (RaftSpec$AppendEntriesResponse/newBuilder)
      (.setTerm (long term))
      (.setSuccess (boolean success))
      .build))

(defn build-vote-request
  [{:keys [term candidate-id last-log-index last-log-term]}]
  (-> (RaftSpec$RequestVote/newBuilder)
      (.setTerm (long term))
      (.setCandidateId (str candidate-id))
      (.setLastLogIndex (long last-log-index))
      (.setLastLogTerm (long last-log-term))
      .build))

(defn build-vote-response [{:keys [term vote-granted]}]
  (-> (RaftSpec$RequestVoteResponse/newBuilder)
      (.setTerm (long term))
      (.setVoteGranted (boolean vote-granted))
      .build))

(defn build-submit-request [command]
  (-> (RaftSpec$SubmitCommand/newBuilder)
      (.setCommand (str command))
      .build))

(defn build-submit-response
  [{:keys [success leader-id committed result]}]
  (-> (RaftSpec$SubmitCommandResponse/newBuilder)
      (.setSuccess (boolean success))
      (.setLeaderId (str (or leader-id "")))
      (.setCommitted (boolean committed))
      (.setResult (str (or result "")))
      .build))

(defn send-grpc-response [respond-to response]
  (.onNext respond-to response)
  (.onCompleted respond-to))

;;; Proto -> Clojure conversion.

(extend-protocol ToClojure
  RaftSpec$LogEntry
  (->clj [this]
    (let [c (.getCommand this)]
      {:term (.getTerm this)
       :command (when (seq c) c)}))

  RaftSpec$AppendEntries
  (->clj [this]
    {:term (.getTerm this)
     :leader-id (.getLeaderId this)
     :prev-log-index (.getPrevLogIndex this)
     :prev-log-term (.getPrevLogTerm this)
     :entries (mapv ->clj (.getEntriesList this))
     :leader-commit (.getLeaderCommit this)})

  RaftSpec$AppendEntriesResponse
  (->clj [this]
    {:term (.getTerm this)
     :success (.getSuccess this)})

  RaftSpec$RequestVote
  (->clj [this]
    {:term (.getTerm this)
     :candidate-id (.getCandidateId this)
     :last-log-index (.getLastLogIndex this)
     :last-log-term (.getLastLogTerm this)})

  RaftSpec$RequestVoteResponse
  (->clj [this]
    {:term (.getTerm this)
     :vote-granted (.getVoteGranted this)})

  RaftSpec$SubmitCommand
  (->clj [this]
    (let [c (.getCommand this)]
      {:command (when (seq c) c)}))

  RaftSpec$SubmitCommandResponse
  (->clj [this]
    {:success (.getSuccess this)
     :leader-id (.getLeaderId this)
     :committed (.getCommitted this)
     :result (.getResult this)}))

;;; gRPC service + client.

(defn service [global-state]
  (proxy [RaftGrpc$RaftImplBase] []
    (appendEntriesRPC [^RaftSpec$AppendEntries request ^StreamObserver response]
      (let [current-state (:current-state @global-state)
            req (->clj request)]
        (if current-state
          (handle-append-entries current-state req response)
          (send-grpc-response response (build-append-response {:term 0 :success false})))))
    (requestVoteRPC [^RaftSpec$RequestVote request ^StreamObserver response]
      (let [current-state (:current-state @global-state)
            req (->clj request)]
        (if current-state
          (handle-vote-request current-state req response)
          (send-grpc-response response (build-vote-response {:term 0 :vote-granted false})))))
    (submitCommandRPC [^RaftSpec$SubmitCommand request ^StreamObserver response]
      (let [current-state (:current-state @global-state)
            req (->clj request)]
        (if current-state
          (handle-submit-command current-state req response)
          (send-grpc-response response (build-submit-response {:success false :leader-id "" :committed false :result ""})))))))

(defn server
  [port global-state]
  (-> (ServerBuilder/forPort port)
      (.addService (service global-state))
      .build
      .start))

(defonce ^:private channels (atom {}))

(defn client
  [host port]
  (let [key [host port]
        channel (or (get @channels key)
                    (let [c (-> (ManagedChannelBuilder/forAddress host port)
                                (.usePlaintext)
                                .build)]
                      (swap! channels assoc key c)
                      c))]
    (RaftGrpc/newBlockingStub channel)))
