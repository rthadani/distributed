(ns distributed.raft.candidate
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [ruiyun.tools.timer :refer [timer run-task! cancel!]]
            [distributed.raft.protocol :refer [RaftState ->clj handle-append-entries handle-vote-request]]
            [distributed.raft.state-factory :as factory]
            [distributed.raft.rpc :as rpc]))

(defn send-election-request
  [global-state id server]
  (let [[host port] (str/split server #":")]
    (future
      (try
        (-> (rpc/client host (Integer/parseInt port))
            (.requestVote (rpc/build-vote-request id @global-state))
            (->clj))
        (catch Exception e
          (println "Exception " (.getMessage e))
          {:term -1 :vote-granted false})))))

(defn do-election
  [{:keys [me servers id election-timeout] :as config} global-state]
  (let [election-timer (timer "election-timer")
        others (set/difference servers #{me})
        election-request-futures (map #(send-election-request global-state id %) others)]

    (run-task! #(do (doall (map future-cancel election-request-futures))
                  (swap! global-state assoc :current-term (inc (:current-term @global-state)))
                  (do-election config global-state))
               :dealy election-timeout :by election-timer)

    (let [all-responses (doall (map #(deref % election-timeout {:term -1 :vote-granted false}) election-request-futures))
          yes-votes (inc (count (map :vote-granted all-responses)))]
      (when (and (= :candidate (:state (:current-state @global-state)))
                 (> yes-votes (count (/ servers 2))))
        (cancel! election-timer)
        (factory/change-state :Leader global-state config)))))

(defrecord Candidate
           [global-state config]
  RaftState
  (state [_] :candidate)
  (init [_]
    (let [{:keys [id]} config]
      (swap! global-state assoc :candidate-id id)
      (do-election config global-state)))
  (handle-append-entries [_ {:keys [term] :as request} respond-to]
    (if (> term (:current-term @global-state))
      (-> (factory/change-state :Follower global-state config)
          (handle-append-entries request respond-to)))
    (handle-vote-request [_ request respond-to])))