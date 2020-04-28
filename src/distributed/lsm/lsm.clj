(ns distributed.lsm
  (:require [clojure.data.priority-map :refer [priority-map]]))

(def p (priority-map :a 2 :b 1 :c 10))
(defn merge-iterators 
  [& iterators]
  )
