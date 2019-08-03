(ns distributed.utils)

(defn index-of
  [predicate coll]
  (keep-indexed
    (fn [idx x]
      (when (predicate x)
        idx)) coll))
