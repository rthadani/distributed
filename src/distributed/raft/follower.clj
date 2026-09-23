(ns distributed.raft.follower
  (:require [distributed.raft.protocol :as proto :refer [RaftState]]
            [distributed.raft.rpc :as rpc]
            [distributed.raft.state :as state]))

(defn- election-timeout-action [global-state config]
  (fn []
    (when (= :follower (proto/state (:current-state @global-state)))
      (state/change-state :Candidate global-state config))))

(defrecord Follower [global-state config]
  RaftState
  (state [_] :follower)

  (init [_]
    (state/reset-election-timer! global-state config
                                 (election-timeout-action global-state config)))

  (handle-append-entries [_ request respond-to]
    (let [{:keys [term leader-id prev-log-index prev-log-term entries leader-commit]} request
          result (state/with-state-lock global-state
                   (let [ct (:current-term @global-state)]
                     (if (< term ct)
                       {:success false}
                       (do
                         (when (> term ct)
                           (swap! global-state assoc :current-term term :voted-for nil))
                         (swap! global-state assoc :leader-id leader-id)
                         ;; A valid AppendEntries (term >= current-term) is a
                         ;; sign of life from a leader; reset the timer before
                         ;; the log-consistency check so both acceptance and
                         ;; rejection suppress a spurious election.
                         (state/reset-election-timer! global-state config
                                                      (election-timeout-action global-state config))
                         (let [new-log (state/append-entries-to-log (:log @global-state)
                                                                     prev-log-index
                                                                     prev-log-term
                                                                     entries)]
                           (if (nil? new-log)
                             {:success false}
                             (do
                               (swap! global-state assoc :log new-log)
                               (let [last-new-index (+ prev-log-index (count entries))]
                                 (when (> leader-commit (:commit-index @global-state))
                                   (swap! global-state assoc :commit-index
                                          (min leader-commit last-new-index))))
                               (state/apply-committed! global-state)
                               {:success true})))))))]
      (rpc/send-grpc-response respond-to
                              (rpc/build-append-response {:term (:current-term @global-state)
                                                           :success (:success result)}))))

  (handle-vote-request [_ request respond-to]
    (let [{:keys [term candidate-id last-log-index last-log-term]} request
          grant? (state/with-state-lock global-state
                   (let [ct (:current-term @global-state)]
                     (cond
                       (< term ct) false
                       :else
                       (do
                         (when (> term ct)
                           (swap! global-state assoc :current-term term :voted-for nil))
                         (let [voted-for (:voted-for @global-state)
                               log-ok (state/log-up-to-date? (:log @global-state)
                                                             last-log-index
                                                             last-log-term)
                               can-vote (or (nil? voted-for) (= voted-for candidate-id))]
                           (when (and can-vote log-ok)
                             (swap! global-state assoc :voted-for candidate-id)
                             (state/reset-election-timer! global-state config
                                                          (election-timeout-action global-state config))
                             true))))))]
      (rpc/send-grpc-response respond-to
                              (rpc/build-vote-response {:term (:current-term @global-state)
                                                         :vote-granted (boolean grant?)}))))

  (handle-submit-command [_ _ respond-to]
    (rpc/send-grpc-response respond-to
                            (rpc/build-submit-response {:success false
                                                        :leader-id (or (:leader-id @global-state) "")
                                                        :committed false
                                                        :result ""}))))
