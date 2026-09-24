(ns distributed.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [distributed.core :refer [foo]]))

(deftest a-test
  (testing "core.foo is defined"
    (is (fn? foo))))
