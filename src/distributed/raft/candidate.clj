(ns distributed.raft.candidate
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [distributed.raft.protocol :refer [RaftState ->clj]]
            #_[distributed.raft.state-factory :as factory]
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
  [me servers id global-state]
  (let [others (set/difference servers #{me})
        election-request-futures (map #(send-election-request global-state id %) others)
        all-responses (map #(deref %) election-request-futures)
        yes-votes (inc (count (map :vote-granted all-responses)))]
    (if (and (= :candidate (:state (:current-state @global-state)))
             (> yes-votes (count (/ servers 2))))
      true)))


(defrecord Candidate
           [global-state config]
  RaftState
  (state [_] :candidate)
  (init [_]
    (let [{:keys [id me servers]} config]
      (swap! global-state assoc :candidate-id id)
      (do-election me servers id global-state)))
  (handle-append-entries [_ request respond-to])
  (handle-vote-request [_ request respond-to]))