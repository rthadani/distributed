(ns distributed.swim.message
  "Pure protobuf <-> Clojure codec for SWIM messages.

  No I/O lives here; distributed.swim.network-peer performs the actual gRPC
  send. Message maps use keyword :type values (:ping, :ack, :ping-req,
  :suspect, :alive, :confirm, :join)."
  (:import (swim SwimSpec$SwimMessage SwimSpec$Update SwimSpec$MsgType)))

(def ^:private kw->enum
  {:ping     SwimSpec$MsgType/PING
   :ack      SwimSpec$MsgType/ACK
   :ping-req SwimSpec$MsgType/PING_REQ
   :suspect  SwimSpec$MsgType/SUSPECT
   :alive    SwimSpec$MsgType/ALIVE
   :confirm  SwimSpec$MsgType/CONFIRM
   :join     SwimSpec$MsgType/JOIN})

(def ^:private enum->kw (into {} (map (fn [[k v]] [v k]) kw->enum)))

;;; Builders.

;; :sequence is the paper's per-period tag; informational under unary gRPC,
;; would enable stale-ACK detection on a datagram transport.
(defn update-entry
  "A single dissemination element (suspect/alive/confirm/join)."
  [member-id incarnation type]
  {:member-id member-id :incarnation incarnation :type type})

(defn msg
  "A message map with sensible defaults for absent fields."
  [type & {:as fields}]
  (merge {:type type :sender-id "" :sequence 0 :target-id ""
          :target-host "" :target-port 0 :incarnation 0 :updates []}
         fields))

;;; Proto -> Clojure.

(defn update->clj [^SwimSpec$Update u]
  {:member-id (.getMemberId u)
   :incarnation (.getIncarnation u)
   :type (enum->kw (.getType u))})

(defn ->clj [^SwimSpec$SwimMessage m]
  {:type (enum->kw (.getType m))
   :sender-id (.getSenderId m)
   :sequence (.getSequence m)
   :target-id (.getTargetId m)
   :target-host (.getTargetHost m)
   :target-port (.getTargetPort m)
   :incarnation (.getIncarnation m)
   :updates (mapv update->clj (.getUpdatesList m))})

;;; Clojure -> Proto.

(defn update->proto [{:keys [member-id incarnation type]}]
  (-> (SwimSpec$Update/newBuilder)
      (.setMemberId (str member-id))
      (.setIncarnation (long incarnation))
      (.setType (kw->enum type))
      .build))

(defn ->proto
  [{:keys [type sender-id sequence target-id target-host target-port
           incarnation updates]}]
  (let [b (doto (SwimSpec$SwimMessage/newBuilder)
            (.setType (or (kw->enum type) SwimSpec$MsgType/ACK))
            (.setSenderId (str (or sender-id "")))
            (.setSequence (long (or sequence 0)))
            (.setTargetId (str (or target-id "")))
            (.setTargetHost (str (or target-host "")))
            (.setTargetPort (int (or target-port 0)))
            (.setIncarnation (long (or incarnation 0))))]
    (doseq [u (or updates [])]
      (.addUpdates b (update->proto u)))
    (.build b)))
