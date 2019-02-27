(ns distributed.merkle-tree
  (:require [clj-message-digest.core :refer [sha-1-hex]]))

(declare get-leaves)
(declare level-order)

(defrecord MerkleTree [v l r sha])

(defn add-to-tree
  [merkle-tree value]
  (let [new-node (MerkleTree. value nil nil (sha-1-hex value))
        make-root-node (fn [l r]
                         (if (nil? r)
                           l
                           (MerkleTree. nil l r (sha-1-hex (str (:sha l) (:sha r))))))
        merkle-tree-internal (fn [current-level]
                               (if (= 1 (count current-level))
                                 (first current-level)
                                 (recur
                                  (->> (partition-all 2 2 current-level)
                                       (mapv #(make-root-node (first %) (second %)))))))
        leaves (get-leaves merkle-tree)]
    (if (or (nil? merkle-tree) (empty? leaves))
      new-node
      (->> new-node
           (conj leaves)
           merkle-tree-internal))))

(defn create-mkl-tree
  [& values]
  (reduce (fn [tree val] (add-to-tree tree val)) nil values))


(defn diff-trees
  [m1 m2]
  (let [lo1 (level-order m1)
        lo2 (level-order m2)
        length-lo1 (count lo1)
        length-lo2 (count lo2)  
        diff-lengths (if (> length-lo1 length-lo2) (subvec lo1 (count lo2)) (subvec lo2 (count lo1)))]))

(defn- get-leaves
  [merkle-tree]
  (let [{:keys [v l r]} merkle-tree]
    (if (and (nil? l) (nil? r))
      [merkle-tree]
      (vec (concat (get-leaves l) (get-leaves r))))))

(defn- children
  [mkl-node]
  (let [left (:l mkl-node)
        right (:r mkl-node)]
    (println left right (not (nil? left)) (seq? right))
    (->>
     (filter #(not (nil? %)) [left right])
     (into []))))

(defn- level-order
  [mkl-tree]
  (loop [queue (conj (clojure.lang.PersistentQueue/EMPTY) mkl-tree)
         result [{:l 0 :c [(:sha mkl-tree)]}]
         level 0]
    (if (empty? queue)
      result
      (let [children (apply concat (map #(children %) queue))
            this-level {:l (inc level) :children (map :sha children)}]
        (recur (into (clojure.lang.PersistentQueue/EMPTY) children)
               (conj result this-level)
               (inc level))))))


#_ (def mkl-tree (create-mkl-tree "file1" "file2" "file3" "file4"))
#_ (children mkl-tree)
#_ (level-order mkl-tree)
