(ns distributed.swim.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [distributed.swim.state :as state]
            [distributed.swim.message :as message]
            [distributed.swim.membership :as membership]))

(deftest parse-id-test
  (testing "valid host:port"
    (is (= {:host "host" :port 9000} (state/parse-id "host:9000")))
    (is (= {:host "127.0.0.1" :port 1} (state/parse-id "127.0.0.1:1"))))
  (testing "unparseable returns nil"
    (is (nil? (state/parse-id "host")))
    (is (nil? (state/parse-id "host:")))
    (is (nil? (state/parse-id "host:abc")))
    (is (nil? (state/parse-id ":9000")))
    (is (nil? (state/parse-id nil)))))

(deftest enqueue-dedups-test
  (let [node (state/new-node (state/node-config 9000))]
    (state/enqueue! node (message/update-entry "a" 0 :alive))
    (state/enqueue! node (message/update-entry "a" 0 :alive))
    (state/enqueue! node (message/update-entry "a" 0 :suspect))
    (state/enqueue! node (message/update-entry "b" 1 :alive))
    (is (= 3 (count (:dissemination @node))))))

(deftest pick-piggyback-cap-and-order-test
  (let [node (state/new-node (assoc (state/node-config 9000) :config {:lambda 1}))
        _ (membership/mark-alive! node "127.0.0.1:9001" 0)
        _ (membership/mark-alive! node "127.0.0.1:9002" 0)]
    (swap! node assoc :dissemination
           [{:member-id "a" :incarnation 0 :type :alive :piggybacked 2}
            {:member-id "b" :incarnation 0 :type :suspect :piggybacked 0}
            {:member-id "c" :incarnation 0 :type :confirm :piggybacked 1}])
    (let [picked (state/pick-piggyback node)]
      ;; N=3, lambda 1 => cap = ceil(1*log2(3)) = 2. Least-gossiped first.
      (is (= #{"b" "c"} (set (map :member-id picked))))
      ;; "a" was already at cap (retired); "c" reached cap this round
      ;; (retired); "b" was gossiped once and remains.
      (is (= ["b"] (mapv :member-id (:dissemination @node))))
      (is (= 1 (:piggybacked (first (:dissemination @node))))))))
