(ns distributed.swim.membership
  "Membership list operations, round-robin probe target selection, and the
   JOIN handshake."
  (:require [distributed.swim.dispatch :refer [handle-message apply-update]]
            [distributed.swim.message :as message]
            [distributed.swim.state :as state]
            [distributed.swim.network-peer :as net]))

;;; Canonical atomic membership-state transitions.
;;
;; Each does its read-compare-write inside a single swap!/swap-vals!, performs
;; no side effects, and returns a truthy value only when the state changed.
;; dissemination.clj and failure_detector.clj call these so the incarnation
;; preference order and suspicion timer live in exactly one place.

(defn mark-alive!
  "Mark `member-id` alive at `incarnation` unless it is confirmed. Adds the
   member when unknown (how members learn of new peers via gossiped ALIVE).
   A suspected member is revived only by a STRICTLY higher incarnation (its
   self-heal bump): a stale same-incarnation ALIVE must not clear a fresh
   suspicion, or the timer would keep resetting and a dead member would never
   be confirmed. Returns truthy when the state changed."
  [node member-id incarnation]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if (contains? (:confirmed s) member-id)
                        s
                        (if-let [m (get-in s [:membership member-id])]
                          (let [apply? (if (= :suspected (:status m))
                                         (> incarnation (:incarnation m))
                                         (>= incarnation (:incarnation m)))]
                            (if apply?
                              (assoc-in s [:membership member-id]
                                        (-> m
                                            (assoc :status :alive :incarnation incarnation)
                                            (dissoc :suspected-since)))
                              s))
                          (if-let [{:keys [host port]} (state/parse-id member-id)]
                            (assoc-in s [:membership member-id]
                                      {:id member-id :host host :port port
                                       :incarnation incarnation :status :alive})
                            s)))))]
    (not= old new)))

(defn unsuspect!
  "Clear suspicion for `member-id` because a direct or indirect probe just
   succeeded. Unlike mark-alive! (a disseminated ALIVE), this is unconditional
   on incarnation: the member just answered a probe, so it is alive now.
   Returns truthy when the state changed."
  [node member-id]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if-let [m (get-in s [:membership member-id])]
                        (assoc-in s [:membership member-id]
                                  (-> m (assoc :status :alive) (dissoc :suspected-since)))
                        s)))]
    (not= old new)))

(defn mark-suspected!
  "Mark `member-id` suspected at `incarnation` unless confirmed or unknown.
   Stores the update's incarnation (needed for the preference order) and sets
   the one-shot suspicion timer only on a NEW suspicion: the existing timer is
   preserved only when re-suspected at the SAME incarnation, and a fresh timer
   is set on the first suspicion and on a strictly higher-incarnation
   re-suspicion (a new suspicion cycle after a self-heal). Returns truthy when
   the state changed."
  [node member-id incarnation]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if (or (contains? (:confirmed s) member-id)
                              (not (contains? (:membership s) member-id)))
                        s
                        (let [m (get-in s [:membership member-id])
                              cur-inc (:incarnation m)]
                          (if (>= incarnation cur-inc)
                            (let [same-suspicion? (and (= :suspected (:status m))
                                                       (= incarnation cur-inc))]
                              (assoc-in s [:membership member-id]
                                        (-> m
                                            (assoc :status :suspected :incarnation incarnation)
                                            (assoc :suspected-since
                                                   (if same-suspicion?
                                                     (:suspected-since m)
                                                     (System/currentTimeMillis))))))
                            s)))))]
    (not= old new)))

(defn mark-failed!
  "CONFIRM `member-id` failed: remove it from membership and add it to the
   :confirmed tombstone set. Never removes self. Returns truthy when the
   member was present and is not self."
  [node member-id]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if (and (not= member-id (:id s))
                               (contains? (:membership s) member-id))
                        (-> s
                            (update :membership dissoc member-id)
                            (update :confirmed conj member-id))
                        s)))]
    (not= old new)))

(defn mark-joined!
  "A member (re)joined: clear its tombstone and add it alive at
   `incarnation`, but only when it is not already a member. A stale JOIN must
   not downgrade a live (alive or suspected) member's status or incarnation,
   nor reset its suspicion timer; it also never joins self. In the same swap
   that re-adds the member, retires any buffered :confirm update for it so a
   stale CONFIRM cannot re-tombstone it after the re-join. Atomic; returns
   truthy when changed."
  [node member-id incarnation]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if (or (= member-id (:id s))
                              (contains? (:membership s) member-id))
                        s
                        (if-let [{:keys [host port]} (state/parse-id member-id)]
                          (-> s
                              (update :confirmed disj member-id)
                              (update :dissemination
                                      (fn [buf]
                                        (into [] (remove #(and (= member-id (:member-id %))
                                                               (= :confirm (:type %))))
                                              buf)))
                              (assoc-in [:membership member-id]
                                        {:id member-id :host host :port port
                                         :incarnation incarnation :status :alive}))
                          s))))]
    (not= old new)))

(defn self-heal!
  "When `member-id` is this node and `incarnation` is not stale, bump own
   incarnation to (inc incarnation), clear suspicion, and return the new
   incarnation. Returns the new incarnation when it changed (which the caller
   then gossips), nil when unchanged."
  [node member-id incarnation]
  (let [[old new] (swap-vals! node
                    (fn [s]
                      (if (and (= member-id (:id s))
                               (not (contains? (:confirmed s) member-id))
                               (>= incarnation (get-in s [:membership member-id :incarnation] -1)))
                        (let [new-inc (inc incarnation)]
                          (-> s
                              (assoc-in [:membership member-id :incarnation] new-inc)
                              (assoc-in [:membership member-id :status] :alive)
                              (update-in [:membership member-id] dissoc :suspected-since)))
                        s)))]
    (when (not= old new)
      (inc incarnation))))

(defn fail-if-expired!
  "If `member-id` is still suspected and its suspicion timer has elapsed,
   CONFIRM it failed (remove + tombstone). Returns truthy when confirmed. The
   check happens inside the swap so a concurrent ALIVE revival is never
   clobbered by a stale sweep."
  [node member-id timeout-ms]
  (let [now (System/currentTimeMillis)
        [old new] (swap-vals! node
                    (fn [s]
                      (if-let [m (get-in s [:membership member-id])]
                        (if (and (not= member-id (:id s))
                                 (= :suspected (:status m))
                                 (:suspected-since m)
                                 (>= (- now (:suspected-since m)) timeout-ms))
                          (-> s
                              (update :membership dissoc member-id)
                              (update :confirmed conj member-id))
                          s)
                        s)))]
    (not= old new)))

(defn member-status [node id]
  (get-in @node [:membership id :status]))

(defn probe-targets
  "Alive or suspected peers, excluding self."
  [node]
  (let [{:keys [id membership]} @node]
    (->> membership vals
         (remove #(= id (:id %)))
         (filter #(#{:alive :suspected} (:status %))))))

(defn random-peers
  "Up to n uniformly random probe targets."
  [node n]
  (->> (probe-targets node) shuffle (take n) vec))

(defn round-robin-target
  "The next probe target: cycle through live peers in a shuffled order,
   reshuffling after each full traversal and whenever membership changes.
   This gives time-bounded completeness (each member is probed once per
   traversal) while keeping random selection's distribution."
  [node]
  (let [{:keys [id membership]} @node
        others (->> membership vals (remove #(= id (:id %))) (mapv :id))]
    (when (seq others)
      (when-not (= (set others) (set (:probe-order @node)))
        (swap! node assoc :probe-order (shuffle others) :probe-index 0))
      (let [{:keys [probe-order probe-index]} @node
            target (nth probe-order probe-index)
            next-idx (mod (inc probe-index) (count probe-order))]
        (swap! node assoc :probe-index next-idx)
        (when (zero? next-idx)
          (swap! node assoc :probe-order (shuffle others)))
        (get membership target)))))

;;; JOIN: server side adds the joiner and seeds its membership list.

(defmethod handle-message :join [node msg]
  (let [joiner-id (:sender-id msg)
        {:keys [host port]} (state/parse-id joiner-id)]
    (when (and host port)
      (mark-joined! node joiner-id 0)
      (state/enqueue! node (message/update-entry joiner-id 0 :join))
      (let [seeds (mapv (fn [[mid m]] (message/update-entry mid (:incarnation m) :alive))
                        (:membership @node))]
        (message/msg :ack
                     :sender-id (:id @node)
                     :target-id joiner-id
                     :updates seeds)))))

;;; JOIN: client side.

(defn join!
  "Join `node` to the group via `contact-id` (host:port), seeding membership
   from the contact's reply. Returns true on success."
  [node contact-id]
  (let [{:keys [id]} @node
        {:keys [host port]} (state/parse-id contact-id)]
    (when (and host port)
      (try
        (let [resp (net/send! host port
                              (message/msg :join
                                           :sender-id id
                                           :target-id contact-id)
                              2000)]
          (when (= :ack (:type resp))
            (doseq [u (:updates resp)] (apply-update node u))
            true))
        (catch Exception _ false)))))
