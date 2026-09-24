(ns distributed.swim.dispatch
  "Message dispatch multimethods for SWIM.

  `node` is a single atom holding all of a member's state. `msg` and `update`
  are plain Clojure maps decoded from protobuf (see distributed.swim.message).

  `handle-message` routes one inbound message and returns a response message
  map, or nil to mean \"no reply\" (the caller's deadline then expires).
  `apply-update` merges one piggybacked dissemination element into the node's
  membership view.

  Methods are defined in the namespaces that own each behavior:
  distributed.swim.membership (join), distributed.swim.failure-detector
  (ping/ping-req), and distributed.swim.dissemination (suspect/alive/confirm).")

(defmulti handle-message
  "Handle one inbound message; returns a response map or nil."
  (fn [node msg] (:type msg)))

(defmulti apply-update
  "Apply one piggybacked dissemination update to `node`."
  (fn [node update] (:type update)))

(defmethod handle-message :default [node msg]
  (println "unknown SWIM message type:" (:type msg))
  nil)

(defmethod apply-update :default [_node _update]
  nil)

(defn apply-updates!
  "Apply every piggybacked update in `updates` to `node`."
  [node updates]
  (doseq [u updates]
    (apply-update node u)))
