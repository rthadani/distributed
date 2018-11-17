(ns distributed.chr
  (:require [clj-message-digest.core :refer [md5]]))

(declare binary-search)

(defrecord Consistent-Hash-Ring [replicas ring])

(defn new-chr
  [replicas]
  (Consistent-Hash-Ring. replicas {}))

(defn assoc-server
  [chr server-id]
  (let [{:keys [replicas ring]} chr
        new-keys (mapv #(-> (str server-id %)
                            md5
                            biginteger
                            (cons [server-id])) (range 0 replicas))]
    (->> (into [] ring)
         (concat new-keys)
         (into (sorted-map))
         (Consistent-Hash-Ring. replicas))))

(defn get-server
  [chr key]
  (as-> (:ring chr) $
    (keys $)
    (binary-search $ 0 (dec count $) key)
    (get (:ring chr) $)))


(defn dissoc-server
  [chr server-id]
  (let [{:keys [replicas ring]} chr]
    (->> ring
         (filter (fn [[_ v]] (= v server-id)))
         (map first)
         (cons ring)
         (apply dissoc)
         (Consistent-Hash-Ring. replicas))))

(defn neighbors
  [chr server-id])

(defn- binary-search
  [ring begin end key]
  (let [mid (/ (+ begin end) 2)]
    (cond
      (= key (ring mid)) (ring mid)
      (and (zero? mid) (< key (ring 0))) (last ring)
      (and (= (mid (dec (count ring)))) (> key (ring mid)) (last ring))
      (and (> key (ring mid)) (< key (ring (inc mid)))) (ring mid)
      (< (ring mid) key) (binary-search ring begin mid)
      :else (binary-search ring (inc mid) end))))

([1 2 3] 0)
(biginteger (md5 "rohit"))

