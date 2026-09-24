(ns distributed.swim.state
  "Per-node state atom, node configuration, and the dissemination buffer.

  A node is a single atom. It is mutated concurrently by the gRPC server
  threads (inbound handlers) and by the protocol-period loop thread, so a
  plain map would race and lose updates. swap!/swap-vals! provide the atomic
  read-modify-write the protocol needs."
  (:require [clojure.string :as str]))

(def default-config
  {:protocol-period-ms 800
   :ack-timeout-ms 300
   :k 2
   :suspicion-timeout-ms 2000
   :lambda 3
   :max-piggyback 6})

(defn node-id [host port] (str host ":" port))

(defn parse-id
  "Split a \"host:port\" member id. Returns nil when unparseable."
  [id]
  (when (string? id)
    (let [i (str/last-index-of id ":")]
      (when (and i (pos? i))
        (let [host (subs id 0 i)
              port-str (subs id (inc i))]
          (when (and (seq host) (seq port-str))
            (try
              {:host host :port (Integer/parseInt port-str)}
              (catch NumberFormatException _ nil))))))))

(defn node-config
  "A node config map, defaulting host to 127.0.0.1 and protocol params to
   `default-config`."
  [port & {:as overrides}]
  (merge {:id (node-id "127.0.0.1" port)
          :host "127.0.0.1"
          :port port}
         overrides))

(defn new-node
  "Create a node atom seeded with itself as the only alive member."
  [{:keys [id host port config]}]
  (atom {:id id :host host :port port
         :config (merge default-config config)
         :membership {id {:id id :host host :port port :incarnation 0 :status :alive}}
         :probe-order [] :probe-index 0 :sequence 0
         :dissemination []
         :confirmed #{}
         :drop-inbound? false
         :server nil :thread nil :running? true}))

;;; Dissemination buffer.

(defn- update-key [u] (select-keys u [:member-id :incarnation :type]))

(defn enqueue!
  "Add `entry` to the dissemination buffer unless it is already queued."
  [node entry]
  (swap! node update :dissemination
         (fn [buf]
           (if (some #(= (update-key %) (update-key entry)) buf)
             buf
             (conj buf (assoc entry :piggybacked 0))))))

(defn- log2 [n] (/ (Math/log (max 2 n)) (Math/log 2)))

(defn max-piggyback
  "The per-element gossip cap: lambda * log2(n), at least 1."
  [node]
  (let [{:keys [config membership]} @node]
    (max 1 (long (Math/ceil (* (:lambda config) (log2 (count membership))))))))

(defn pick-piggyback
  "Choose up to `max-piggyback` updates for the next outbound message,
   preferring the least-gossiped ones, increment their counters, and retire
   any that reached the cap. Returns update maps without :piggybacked.
   Selection and increment happen in one swap-vals! so concurrent callers do
   not under-count and gossip past the cap."
  [node]
  (let [limit (max-piggyback node)
        [old _] (swap-vals! node
                  (fn [s]
                    (let [selected (->> (:dissemination s) (sort-by :piggybacked) (take limit))
                          sel-keys (set (map update-key selected))]
                      (assoc s :dissemination
                             (->> (:dissemination s)
                                  (map (fn [u]
                                         (if (contains? sel-keys (update-key u))
                                           (update u :piggybacked inc)
                                           u)))
                                  (remove #(>= (:piggybacked %) limit))
                                  vec)))))
        selected (->> (:dissemination old) (sort-by :piggybacked) (take limit))]
    (mapv #(dissoc % :piggybacked) selected)))
