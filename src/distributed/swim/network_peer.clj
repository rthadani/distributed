(ns distributed.swim.network-peer
  "gRPC transport for SWIM: the Send service, the server, cached blocking
   stubs, and send!. Mirrors the raft gRPC wiring in distributed.raft.rpc."
  (:require [distributed.swim.dispatch :refer [handle-message]]
            [distributed.swim.message :as message])
  (:import (swim SwimGrpc$SwimImplBase SwimGrpc SwimSpec$SwimMessage)
           (io.grpc ServerBuilder ManagedChannelBuilder)
           (io.grpc.stub StreamObserver)
           (java.util.concurrent TimeUnit)))

(defn- send-grpc-response [^StreamObserver response ^SwimSpec$SwimMessage msg]
  (.onNext response msg)
  (.onCompleted response))

(defn service
  "The gRPC service handling inbound Send calls for `node`.

   When `:drop-inbound?` is true the node simulates being down/overloaded: it
   replies to nothing, so callers' deadlines expire (used by the demo to model
   an unresponsive member). A nil handler result has the same effect."
  [node]
  (proxy [SwimGrpc$SwimImplBase] []
    (send [^SwimSpec$SwimMessage request ^StreamObserver response]
      (when-not (:drop-inbound? @node)
        (let [msg (message/->clj request)
              resp (handle-message node msg)]
          ;; A nil response also simulates a dropped message.
          (when resp
            (send-grpc-response response (message/->proto resp))))))))

(defn server
  "Start the gRPC server for `node`; returns the started Server."
  [node]
  (-> (ServerBuilder/forPort (:port @node))
      (.addService (service node))
      .build
      .start))

(defonce ^:private channels (atom {}))

(defn client
  "A cached blocking stub for host:port."
  [host port]
  (let [key [host port]
        channel (or (get @channels key)
                    (let [c (-> (ManagedChannelBuilder/forAddress host port)
                                (.usePlaintext)
                                .build)]
                      (swap! channels assoc key c)
                      c))]
    (SwimGrpc/newBlockingStub channel)))

(defn send!
  "Send `msg` to host:port and return the response map. With `timeout-ms`, the
   call carries a deadline (the ping/ack timeout); a deadline or unavailable
   peer throws StatusRuntimeException."
  ([host port msg] (send! host port msg nil))
  ([host port msg timeout-ms]
   (let [stub (client host (int port))
         stub (if timeout-ms
                (.withDeadlineAfter stub (long timeout-ms) TimeUnit/MILLISECONDS)
                stub)]
     (message/->clj (.send stub (message/->proto msg))))))
