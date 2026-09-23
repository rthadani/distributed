(ns distributed.raft.raft-test
  (:require [clojure.test :refer :all]
            [distributed.raft.main :as main]
            [distributed.raft.client :as client]
            [distributed.raft.protocol :as proto]
            [distributed.raft.state :as state]
            [distributed.raft.follower :as follower])
  (:import (io.grpc.stub StreamObserver)))

;;; Pure unit tests.

(deftest append-entries-to-log-truncates-conflicting-suffix
  ;; A conflicting entry at index 2 must delete it AND the trailing c(2);
  ;; a naive overwrite-from-prevLogIndex+1 would keep c(2).
  (let [log [{:term 1 :command "a"} {:term 2 :command "b"} {:term 2 :command "c"}]
        result (state/append-entries-to-log log 1 1 [{:term 3 :command "b'"}])]
    (is (= [{:term 1 :command "a"} {:term 3 :command "b'"}] result))))

(deftest append-entries-to-log-rejects-prev-log-term-mismatch
  (let [log [{:term 1 :command "a"} {:term 2 :command "b"}]]
    (is (nil? (state/append-entries-to-log log 2 99 [{:term 3 :command "c"}])))))

(deftest append-entries-to-log-appends-new-entries
  (let [log [{:term 1 :command "a"}]
        result (state/append-entries-to-log log 1 1 [{:term 1 :command "b"}])]
    (is (= [{:term 1 :command "a"} {:term 1 :command "b"}] result))))

(deftest append-entries-to-log-empty-entries-are-a-noop
  ;; A heartbeat must never truncate the log, even when the follower is ahead
  ;; of the leader's prev-log-index.
  (let [log [{:term 1 :command "a"} {:term 2 :command "b"} {:term 2 :command "c"}]]
    (is (= log (state/append-entries-to-log log 1 1 [])))
    (is (= log (state/append-entries-to-log log 2 2 [])))))

(deftest log-up-to-date-semantics
  ;; empty vs empty is up-to-date
  (is (state/log-up-to-date? [] 0 0))
  ;; higher last term wins regardless of length
  (is (state/log-up-to-date? [{:term 1 :command "a"} {:term 1 :command "b"}] 1 2))
  ;; equal term and a longer log wins
  (is (state/log-up-to-date? [{:term 1 :command "a"}] 2 1))
  ;; equal term but a shorter log is NOT up-to-date
  (is (not (state/log-up-to-date? [{:term 1 :command "a"} {:term 1 :command "b"}] 1 1)))
  ;; lower term is NOT up-to-date even with a longer log
  (is (not (state/log-up-to-date? [{:term 2 :command "a"}] 5 1))))

(deftest advance-commit-index-only-commits-current-term
  ;; Index 2 has term 1 (old term): even with a majority it must not commit.
  (let [log [{:term 1 :command "a"} {:term 1 :command "b"} {:term 2 :command "c"}]
        match {1 2, 2 2, :self 3}]
    (is (= 0 (state/advance-commit-index 0 2 log match))))
  ;; Index 2 has current term and a majority: it commits.
  (let [log [{:term 1 :command "a"} {:term 2 :command "b"}]
        match {1 2, 2 1, :self 2}]
    (is (= 2 (state/advance-commit-index 0 2 log match)))))

(deftest state-file-round-trips-nil-command
  (let [f (str "data/test-roundtrip-" (System/nanoTime) ".edn")
        s {:current-term 7 :voted-for "127.0.0.1:8001"
           :log [{:term 7 :command nil} {:term 7 :command "SET x=1"}]}]
    (try
      (state/persist-state! f s)
      (is (= s (state/load-state! f)) "nil command and voted-for round-trip")
      (finally (some-> (java.io.File. f) .delete)))))

;;; Integration tests (real gRPC servers).

(defn- capturing-observer []
  (let [captured (atom nil)]
    {:observer (reify StreamObserver
                 (onNext [_ v] (reset! captured v))
                 (onError [_ _] nil)
                 (onCompleted [_] nil))
     :captured captured}))

(defn- test-config [port servers]
  (main/node-config port
                    :servers servers
                    :election-timeout-ms 300
                    :heartbeat-ms 50
                    :state-file (str "data/test-raft-" port "-" (System/nanoTime) ".edn")))

(defn- boot-cluster [ports]
  (let [servers (set (map #(str "127.0.0.1:" %) ports))]
    (mapv #(main/start-node! (test-config % servers)) ports)))

(defn- shutdown-cluster [nodes]
  (doseq [node nodes]
    (try (main/stop-node! node) (catch Exception _))
    (some-> (:state-file (:config node)) (java.io.File.) .delete)))

(defn- leader [nodes]
  (some (fn [node]
          (when (= :leader (proto/state (:current-state @(:global-state node))))
            node))
        nodes))

(defn- wait-until [timeout-ms f]
  (loop [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (let [v (f)]
      (if (or v (>= (System/currentTimeMillis) deadline))
        v
        (do (Thread/sleep 50) (recur deadline))))))

(deftest exactly-one-leader-is-elected
  (let [nodes (boot-cluster [21001 21002 21003])]
    (try
      (is (wait-until 5000 #(leader nodes)) "a leader should be elected")
      (let [leaders (filter #(= :leader (proto/state (:current-state @(:global-state %)))) nodes)]
        (is (= 1 (count leaders)) "exactly one leader"))
      (finally (shutdown-cluster nodes)))))

(deftest commands-replicate-to-all-nodes
  (let [nodes (boot-cluster [21101 21102 21103])]
    (try
      (is (wait-until 5000 #(leader nodes)))
      (let [resp (client/submit-command "127.0.0.1:21101" "SET k=1")]
        (is (:success resp) (str "submit succeeds: " resp)))
      (is (wait-until 5000
                      #(every? (fn [n] (some #{"SET k=1"} (:applied @(:global-state n)))) nodes))
          "all nodes apply the replicated command")
      (finally (shutdown-cluster nodes)))))

(deftest submit-to-follower-redirects-to-leader
  (let [nodes (boot-cluster [21201 21202 21203])]
    (try
      (is (wait-until 5000 #(leader nodes)))
      (let [ldr (leader nodes)
            follower (first (remove #(= (:port (:config %)) (:port (:config ldr))) nodes))]
        (is follower "a non-leader exists")
        (is (wait-until 5000 #(seq (:leader-id @(:global-state follower))))
            "follower learns the leader id")
        (let [resp (client/submit-command (:me (:config follower)) "SET k=2")]
          (is (:success resp) (str "redirected submit succeeds: " resp))
          (is (seq (:leader-id resp)) "response names a leader")))
      (finally (shutdown-cluster nodes)))))

(deftest cluster-elects-new-leader-after-old-leader-dies
  (let [ports [21301 21302 21303]
        nodes (boot-cluster ports)]
    (try
      (is (wait-until 5000 #(leader nodes)))
      (let [old-leader (leader nodes)
            old-port (:port (:config old-leader))]
        (main/stop-node! old-leader)
        (let [live (remove #(= (:port (:config %)) old-port) nodes)]
          (is (wait-until 8000 #(leader live)) "a survivor is elected leader")
          (let [new-leader (leader live)]
            (is new-leader "a new leader is elected")
            (when new-leader
              (let [resp (client/submit-command (:me (:config new-leader)) "SET k=3")]
                (is (:success resp) (str "submit after failover succeeds: " resp)))))))
      (finally (shutdown-cluster nodes)))))

(deftest cluster-commits-with-a-majority-when-one-node-is-down
  (let [ports [21401 21402 21403]
        nodes (boot-cluster ports)]
    (try
      (is (wait-until 5000 #(leader nodes)))
      (let [ldr (leader nodes)
            victim (first (remove #(= (:port (:config %)) (:port (:config ldr))) nodes))
            live (remove #(= (:port (:config %)) (:port (:config victim))) nodes)]
        (main/stop-node! victim)
        (let [resp (client/submit-command (:me (:config ldr)) "SET k=majority")]
          (is (:success resp) (str "submit succeeds with one node down: " resp))
          (is (wait-until 5000
                          #(every? (fn [n] (some #{"SET k=majority"} (:applied @(:global-state n)))) live))
              "both surviving nodes commit the command")))
      (finally (shutdown-cluster nodes)))))

(deftest follower-grants-vote-only-once-per-term
  (let [gs (state/new-node-state {})
        config {:me "127.0.0.1:23000"
                :servers #{"127.0.0.1:23000" "127.0.0.1:23001" "127.0.0.1:23002"}
                :election-timeout-ms 1000
                :heartbeat-ms 100}
        rec (follower/->Follower gs config)
        req {:term 5 :candidate-id "127.0.0.1:23001" :last-log-index 0 :last-log-term 0}]
    (try
      (let [c1 (capturing-observer)]
        (proto/handle-vote-request rec req (:observer c1))
        (is (:vote-granted (proto/->clj @(:captured c1))) "first vote is granted"))
      (let [c2 (capturing-observer)]
        (proto/handle-vote-request rec (assoc req :candidate-id "127.0.0.1:23002") (:observer c2))
        (is (not (:vote-granted (proto/->clj @(:captured c2)))) "second vote in the same term is rejected"))
      (finally
        (state/cancel-election-timer! gs)
        (some-> (:scheduler @gs) .shutdownNow)
        (some-> (:rpc-executor @gs) .shutdownNow)))))

(deftest node-recovers-term-and-log-from-state-file
  (let [ports [21501 21502 21503]
        run-id (str (System/nanoTime))
        files (mapv #(str "data/test-recovery-" % "-" run-id ".edn") ports)
        servers (set (map #(str "127.0.0.1:" %) ports))
        configs (mapv (fn [p f] (main/node-config p :servers servers :state-file f)) ports files)
        nodes (mapv main/start-node! configs)]
    (try
      (is (wait-until 5000 #(leader nodes)))
      (let [resp (client/submit-command "127.0.0.1:21501" "SET persist=1")]
        (is (:success resp) (str "submit: " resp)))
      (is (wait-until 5000
                      #(every? (fn [n] (some #{"SET persist=1"} (:applied @(:global-state n)))) nodes)))
      (doseq [n nodes] (try (main/stop-node! n) (catch Exception _)))
      (let [gs0 @(:global-state (first nodes))
            term (:current-term gs0)
            log (:log gs0)
            rebooted (main/start-node! (first configs))]
        (try
          (let [gs1 @(:global-state rebooted)]
            (is (= term (:current-term gs1)) "current-term recovered")
            (is (= log (:log gs1)) "log recovered"))
          (finally (try (main/stop-node! rebooted) (catch Exception _)))))
      (finally
        (doseq [f files] (some-> (java.io.File. f) .delete))))))
