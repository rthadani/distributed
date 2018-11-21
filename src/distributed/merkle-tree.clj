(ns distributed.merkle-tree
    (:require [clj-message-digest.core :refer [sha-1-hex]]))

(declare get-leaves)

(defrecord MerkleTree [v l r sha])

(defn add-to-tree
  [merkle-tree value]
  (let [ new-node (MerkleTree. value nil nil (sha-1-hex (value)))  
        make-root-node (fn [l r]
                         (if (nil? r)
                           l
                           (MerkleTree. l r (sha-1-hex (str (:sha l) (:sha r))))))
        merkle-tree-internal (fn [current-level]
                               (if (= 1 (count current-level))
                                 (first current)
                                 (recur
                                  (->> (partition 2 2 current-level)
                                       (map #(make-root-node (first %) (second %)))))))
        leaves (get-leaves merkle-tree)]
       (if (empty? leaves)
         new-node
         (-> new-node
            (conj leaves)
            merkle-tree-internal)) ))

(defn- get-leaves
  [merkle-tree]
  (let [{:keys [v l r]} merkle-tree]
    (if (and (nil? l) (nil? r) [merkle-tree])
           (concat (get-leaves l) (get-leaves r))))
