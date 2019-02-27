(ns distributed.vector-clock
  (:require [clojure.set :as set]))


(declare timestamps-valid?)
(declare version-for-id)
(declare map-to-timestamps)

(defprotocol VectorClockProtocol
  (descendant? [this, other])
  (ancestor? [this other])
  (conflict? [this other])
  (update-clock [this id message])
  (merge-clock [this other modifier-id message]))

(defrecord VectorClockTimestamp [id version])


(defrecord VectorClock [timestamps message]
  VectorClockProtocol
  (descendant?
    [this other]
    (or (empty? (:timestamps this))
        (timestamps-valid? other this)))

  (ancestor?
    [this other]
    (or (empty? (:timestamps this))
        (timestamps-valid? this other)))

  (conflict?
    [this other]
    (and (not (ancestor? this other)) (not (descendant? this other))))

  (update-clock
    [_ new-id message]
    (let [ts-set (into #{} (map :id timestamps))]
      (if-not (contains? ts-set new-id)
        (VectorClock. (conj timestamps (VectorClockTimestamp. new-id 1)) message)
        (-> (reduce
             (fn [vc {:keys [id version]}]
               (if (= id new-id)
                 (conj vc (VectorClockTimestamp. id (inc version)))
                 (conj vc (VectorClockTimestamp. id version))))
             []
             timestamps)
            (VectorClock. message)))))

  (merge-clock
    [this other modifier-id message]
    (let [this-timestamp-as-map (reduce (fn [m {:keys [id version]}] (assoc m id version)) {} timestamps)
          other-timestamp-as-map (reduce (fn [m {:keys [id version]}] (assoc m id version)) {} (:timestamps other))]
      (if (get this-timestamp-as-map modifier-id)
        (-> (merge-with (fn [v1 v2] (if (> v1 v2) v1 v2)) this-timestamp-as-map other-timestamp-as-map)
            (update modifier-id inc)
            (map-to-timestamps)
            (VectorClock. message))
        this))))

(defn timestamps-valid?
  [old new]
  (let [{new-timestamps :timestamps} new
        {old-timestamps :timestamps} old
        old-ids (into #{} (map :id old-timestamps))
        new-ids (into #{} (map :id new-timestamps))]
    (and (>= (count new-timestamps) (count old-timestamps))
         (set/subset? old-ids new-ids)
         (every? #(>= (version-for-id % new-timestamps) (version-for-id % old-timestamps)) old-ids))))


(defn- version-for-id
  [id timestamps]
  (->> timestamps
       (filter #(= (:id %) id))
       first
       :version))

(defn- map-to-timestamps [ts-as-map]
  (map (fn [[id version]] (->VectorClockTimestamp id version)) ts-as-map))

#_ (def alice (->VectorClock [(VectorClockTimestamp. :Alice 1)] "Lunch Wednesday?"))
#_ alice
#_ (def ben (update-clock alice :Ben "How about Tuesday!"))
#_ ben
#_ (def dave (update-clock ben :Dave "Tuesday is good"))
#_ dave
#_ (descendant? dave alice)
#_ (descendant? alice dave)
#_ (ancestor? alice dave)
#_ (version-for-id :Ben (:timestamps dave))
#_ (def cathy (update-clock alice :Cathy "Thursday?"))
#_ cathy
#_ (conflict? dave cathy)
#_ (def conflict-resolved (merge-clock dave cathy :Dave "Thursday works"))
#_ conflict-resolved
#_ (descendant? conflict-resolved dave)