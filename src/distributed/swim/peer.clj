(ns distributed.swim.peer
  (:require [manifold.stream :as s]
            [clojure.edn :as edn]
            [aleph.tcp :as tcp]
            [gloss.core :as gloss]
            [gloss.io :as io]
            [manifold.deferred :as d]
            [distributed.swim.message :as message]
            [clojure.core.async :as async]
            [distributed.swim.protocols :refer [Peer]]
            [core.async :refer [!!>]]))

;incoming message handler
(defmulti peer-message-handler
          (fn [msg _ _ _] (:type msg)))
(defmethod peer-message-handler :ping
  [msg peer membership-list id]
  (println msg)
  #_(update-membership-list membership-list (:member-list msg))
  (s/put! peer (message/ping-ack-msg :ack id membership-list)))
(defmethod peer-message-handler :delegate-ack
  [msg peer membership-list id])
(defmethod peer-message-handler :update
  [msg _ membership-list _])
(defmethod peer-message-handler :default
  [msg _ _ _]
  (println "unknown message" msg))


;;All the communication things
(defn peer-handler
  [peer _ membership-list id]
  (s/map #(peer-message-handler % peer membership-list id) peer))

(def protocol
  (gloss/compile-frame
    (gloss/finite-frame :uint32 (gloss/string :utf-8))
    pr-str
    edn/read-string))

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(io/encode protocol %) out)
      s)
    (s/splice
      out
      (io/decode-stream s protocol))))

(defn start-tcp-server
  [port id membership-list]
  (tcp/start-server
    (fn [s info]
      (peer-handler (wrap-duplex-stream protocol s) info id membership-list))
    {:port port}))

(defn connect-tcp-peer
  [host port]
  @(d/chain (tcp/client {:host host :port port})
            #(wrap-duplex-stream protocol %)))

#_ (def membership-list [{:id "1" :status :up :host "localhost" :port 10000}
                         {:id "2" :status :down :host "localhost" :port 10001}])

(defn direct-ping
  [host port id timeout-ms membership-list]
  (when-let [peer (connect-tcp-peer host port)]
    (d/chain (s/try-put! peer (message/ping-ack-msg :ping id membership-list) timeout-ms)
             #(when % (s/try-take! peer timeout-ms)))))


(defn indirect-ping
  [host port timeout-ms membership-list]
  (let ()))


(defn send-ping-message
  [id timeout-ms membership-list {:keys [host port]}]
  (if-let [mlist @(direct-ping host port id timeout-ms @membership-list)]
    (update-membership-list membership-list mlist)
    (indirect-ping host port timeout-ms @membership-list)))

(defrecord ChannelPeer [id state ch]
  Peer
  (get-id [_] id)
  (get-state [_] state)
  (send-message-with-ack [_ other message]
     (!!> ))
  (receive-message [_ other])
  (connect [_ other]))