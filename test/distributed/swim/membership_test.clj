(ns distributed.swim.membership-test
  (:require [clojure.test :refer [deftest is]]
            [distributed.swim.state :as state]
            [distributed.swim.dispatch :refer [handle-message]]
            [distributed.swim.membership :as membership]))

(def member-a "127.0.0.1:9001")
(def member-b "127.0.0.1:9002")

(defn- fresh-node []
  (state/new-node (state/node-config 9000)))

(deftest one-shot-suspicion-timer-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    (membership/mark-suspected! node member-a 0)
    (is (= :suspected (get-in @node [:membership member-a :status])))
    (is (some? (get-in @node [:membership member-a :suspected-since])))
    ;; pin a sentinel and re-suspect at the same incarnation; the one-shot
    ;; timer must not reset
    (swap! node assoc-in [:membership member-a :suspected-since] 12345)
    (membership/mark-suspected! node member-a 0)
    (is (= 12345 (get-in @node [:membership member-a :suspected-since])))))

(deftest tombstone-and-join-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    (membership/mark-failed! node member-a)
    (is (nil? (get-in @node [:membership member-a])))
    (is (contains? (:confirmed @node) member-a))
    ;; stale ALIVE/SUSPECT cannot resurrect a confirmed member
    (is (false? (membership/mark-alive! node member-a 1)))
    (is (false? (membership/mark-suspected! node member-a 1)))
    (is (nil? (get-in @node [:membership member-a])))
    ;; a JOIN clears the tombstone and re-adds the member
    (let [resp (handle-message node {:type :join
                                     :sender-id member-a
                                     :target-id "127.0.0.1:9000"})]
      (is (= :ack (:type resp)))
      (is (some? (get-in @node [:membership member-a])))
      (is (not (contains? (:confirmed @node) member-a))))))

(deftest round-robin-target-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    (membership/mark-alive! node member-b 0)
    ;; one full traversal returns each other member exactly once
    (let [targets (repeatedly 2 #(:id (membership/round-robin-target node)))]
      (is (= #{member-a member-b} (set targets))))
    ;; a membership change reshuffles the order to include the new member
    (membership/mark-alive! node "127.0.0.1:9003" 0)
    (membership/round-robin-target node)
    (is (= #{member-a member-b "127.0.0.1:9003"}
           (set (:probe-order @node))))))

(deftest fail-if-expired-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    (membership/mark-suspected! node member-a 0)
    ;; unexpired: leave alone
    (swap! node assoc-in [:membership member-a :suspected-since]
           (System/currentTimeMillis))
    (is (false? (membership/fail-if-expired! node member-a 1000)))
    (is (some? (get-in @node [:membership member-a])))
    ;; expired: remove + tombstone
    (swap! node assoc-in [:membership member-a :suspected-since]
           (- (System/currentTimeMillis) 2000))
    (is (true? (membership/fail-if-expired! node member-a 1000)))
    (is (nil? (get-in @node [:membership member-a])))
    (is (contains? (:confirmed @node) member-a))
    ;; a member revived to :alive is never expired by the sweep
    (membership/mark-alive! node member-b 0)
    (membership/mark-suspected! node member-b 0)
    (membership/unsuspect! node member-b)
    (is (false? (membership/fail-if-expired! node member-b 1000)))
    (is (some? (get-in @node [:membership member-b])))))
