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
        new-keys (mapv (fn [key-idx]
                         [(->> (str server-id key-idx) md5 bigint) server-id]) 
                       (range 0 replicas))]
    (->> (into [] ring)
       (concat new-keys)
       (into (sorted-map))
       (Consistent-Hash-Ring. replicas))))

(defn get-server
  [chr key]
  (let [ring-key (-> (str key) md5 bigint)]
    (->> (:ring chr)
         (keys)
         (into [])
         (binary-search ring-key)
         (get (:ring chr)))))

(defn dissoc-server
  [chr server-id]
  (let [{:keys [replicas ring]} chr]
    (->> ring
         (filter (fn [[_ v]] (= v server-id)))
         (map first)
         (cons ring)
         (apply dissoc)
         (Consistent-Hash-Ring. replicas))))

;;for hinted handoffs
(defn neighbors
  [chr replica-id]
  (let [{}]))

(defn- binary-search
  ([key ring]
   (let [end (dec (count ring))
       result (and (pos? end) (binary-search ring 0 end key))]
     (cond
       (neg? end) nil
       (not= result -1) result
       (< key (ring 0)) (last ring)
       (> key (ring end))(first ring))))
  ([ring begin end key]
   (let [mid (int (/ (+ begin end) 2))]
     (cond
       (>= begin end) -1
       (= (ring mid) key) (ring mid)
       (and (< 0 mid (count ring)) (> key (ring (dec mid))) (< key (ring (inc mid)))) (ring mid)
       (< key (ring mid)) (binary-search ring begin mid key)
       :else (binary-search ring (inc mid) end key)))))

#_(binary-search 0 [1 2 4 5])
#_(binary-search (bigint 0) (mapv #(bigint %) (range 1 6)))
#_(binary-search 3 [1 2 3 4 5])
#_(binary-search 6 [1 2 4 5])
#_(binary-search 3 [1 2 4 5])
#_(binary-search 1 [1 2 4 5])
#_(binary-search 1 [])
#_(def chr (-> (new-chr 3)
              (assoc-server "192.168.0.1")
              (assoc-server "192.168.0.2")))

#_ (get-server chr "file3")          
#_ (get-server chr "file1")          

#_(dissoc-server chr "192.168.0.1")
