(ns distributed.swim.dissemination-test
  (:require [clojure.test :refer [deftest is]]
            [distributed.swim.state :as state]
            [distributed.swim.dispatch :refer [apply-update]]
            [distributed.swim.membership :as membership]
            [distributed.swim.dissemination]))

(def member-a "127.0.0.1:9001")

(defn- fresh-node []
  (state/new-node (state/node-config 9000)))

(deftest incarnation-preference-order-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    ;; higher incarnation ALIVE applies
    (apply-update node {:type :alive :member-id member-a :incarnation 1})
    (is (= :alive (get-in @node [:membership member-a :status])))
    (is (= 1 (get-in @node [:membership member-a :incarnation])))
    ;; lower incarnation ALIVE ignored
    (apply-update node {:type :alive :member-id member-a :incarnation 0})
    (is (= 1 (get-in @node [:membership member-a :incarnation])))
    ;; SUSPECT stores its incarnation
    (apply-update node {:type :suspect :member-id member-a :incarnation 2})
    (is (= :suspected (get-in @node [:membership member-a :status])))
    (is (= 2 (get-in @node [:membership member-a :incarnation])))
    ;; stale lower-incarnation ALIVE after newer SUSPECT is ignored
    (apply-update node {:type :alive :member-id member-a :incarnation 1})
    (is (= :suspected (get-in @node [:membership member-a :status])))
    (is (= 2 (get-in @node [:membership member-a :incarnation])))
    ;; CONFIRM is unconditional: removes + tombstones
    (apply-update node {:type :confirm :member-id member-a :incarnation 2})
    (is (nil? (get-in @node [:membership member-a])))
    (is (contains? (:confirmed @node) member-a))
    ;; resurrection is blocked after CONFIRM
    (apply-update node {:type :alive :member-id member-a :incarnation 3})
    (is (nil? (get-in @node [:membership member-a])))))

(deftest same-incarnation-alive-does-not-clear-suspicion-test
  (let [node (fresh-node)]
    (membership/mark-alive! node member-a 0)
    (membership/mark-suspected! node member-a 0)
    ;; a stale same-incarnation ALIVE must not revive a suspected member
    (apply-update node {:type :alive :member-id member-a :incarnation 0})
    (is (= :suspected (get-in @node [:membership member-a :status])))
    ;; a strictly higher incarnation (the self-heal bump) does revive
    (apply-update node {:type :alive :member-id member-a :incarnation 1})
    (is (= :alive (get-in @node [:membership member-a :status])))
    (is (= 1 (get-in @node [:membership member-a :incarnation])))))
