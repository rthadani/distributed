(ns distributed.raft.candidate
  (:require [clojure.string :as str]
            [distributed.raft.protocol :as proto :refer [RaftState]]
            [distributed.raft.rpc :as rpc]
            [distributed.raft.state :as state]))

(defn- peers [config] (disj (:servers config) (:me config)))

(defn- won-election? [global-state config]
  (>= (count (:votes @global-state))
      (state/majority (count (:servers config)))))

(defn- handle-vote-response! [global-state config peer resp]
  (let [resp-term (:term resp)]
    (if (state/step-down-on-term! global-state config resp-term >)
      nil
      (state/with-state-lock global-state
        (when (and (= :candidate (proto/state (:current-state @global-state)))
                   (= resp-term (:current-term @global-state))
                   (:vote-granted resp))
          (swap! global-state update :votes (fnil conj #{}) peer)
          (when (won-election? global-state config)
            (state/change-state :Leader global-state config)))))))

(defn- request-vote! [global-state config peer vote-req]
  (try
    (let [[host port] (str/split peer #":")
          resp (-> (rpc/client host (Integer/parseInt port))
                   (.requestVoteRPC (rpc/build-vote-request vote-req))
                   (proto/->clj))]
      (handle-vote-response! global-state config peer resp))
    (catch Exception e
      (println "RequestVote to" peer "failed:" (.getMessage e)))))

(defn- start-election! [global-state config]
  (let [{:keys [current-term log]} @global-state
        vote-req {:term current-term
                  :candidate-id (:me config)
                  :last-log-index (count log)
                  :last-log-term (or (state/log-last-term log) 0)}
        others (peers config)
        executor (:rpc-executor @global-state)]
    (doseq [peer others]
      (.execute executor (fn [] (request-vote! global-state config peer vote-req))))))

(defn- candidate-timeout! [global-state config]
  (state/with-state-lock global-state
    (when (= :candidate (proto/state (:current-state @global-state)))
      (swap! global-state assoc
             :current-term (inc (:current-term @global-state))
             :voted-for (:me config)
             :votes #{(:me config)})
      (state/reset-election-timer! global-state config (fn [] (candidate-timeout! global-state config)))
      (start-election! global-state config))))

(defrecord Candidate [global-state config]
  RaftState
  (state [_] :candidate)

  (init [_]
    (state/with-state-lock global-state
      (swap! global-state assoc
             :current-term (inc (:current-term @global-state))
             :voted-for (:me config)
             :leader-id nil
             :leader-volatile nil
             :votes #{(:me config)})
      (state/reset-election-timer! global-state config (fn [] (candidate-timeout! global-state config)))
      (.execute (:rpc-executor @global-state) (fn [] (start-election! global-state config)))))

  (handle-append-entries [_ request respond-to]
    (let [{:keys [term]} request]
      (if (state/step-down-on-term! global-state config term >=)
        (proto/handle-append-entries (:current-state @global-state) request respond-to)
        (rpc/send-grpc-response respond-to
                                (rpc/build-append-response {:term (:current-term @global-state)
                                                             :success false})))))

  (handle-vote-request [_ request respond-to]
    (let [{:keys [term]} request]
      (if (state/step-down-on-term! global-state config term >)
        (proto/handle-vote-request (:current-state @global-state) request respond-to)
        (rpc/send-grpc-response respond-to
                                (rpc/build-vote-response {:term (:current-term @global-state)
                                                           :vote-granted false})))))

  (handle-submit-command [_ _ respond-to]
    (rpc/send-grpc-response respond-to
                            (rpc/build-submit-response {:success false
                                                        :leader-id (or (:leader-id @global-state) "")
                                                        :committed false
                                                        :result ""}))))
