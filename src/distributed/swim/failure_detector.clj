(ns distributed.swim.failure-detector
  "SWIM failure detector: ping/ack handling, indirect probing, the
   protocol-period loop, and node lifecycle."
  (:require [distributed.swim.dispatch :refer [handle-message apply-updates!]]
            [distributed.swim.message :as message]
            [distributed.swim.state :as state]
            [distributed.swim.membership :as membership]
            [distributed.swim.network-peer :as net])
  (:import (io.grpc StatusRuntimeException)))

;;; Inbound handlers.

(defmethod handle-message :ping [node msg]
  (apply-updates! node (:updates msg))
  (message/msg :ack
               :sender-id (:id @node)
               :sequence (:sequence msg)
               :target-id (:sender-id msg)
               :target-host (:host @node)
               :target-port (:port @node)
               :updates (state/pick-piggyback node)))

(defn- negative-ping-req [node msg]
  (message/msg :ping-req
               :sender-id (:id @node)
               :sequence (:sequence msg)
               :target-id (:target-id msg)
               :updates (state/pick-piggyback node)))

(defmethod handle-message :ping-req [node msg]
  (apply-updates! node (:updates msg))
  (let [ping (message/msg :ping
                          :sender-id (:id @node)
                          :sequence (:sequence msg)
                          :target-id (:target-id msg)
                          :target-host (:target-host msg)
                          :target-port (:target-port msg)
                          :updates (state/pick-piggyback node))]
    (try
      (let [resp (net/send! (:target-host msg) (:target-port msg) ping
                            (:ack-timeout-ms (:config @node)))]
        (apply-updates! node (:updates resp))
        (if (= :ack (:type resp))
          (message/msg :ack
                       :sender-id (:id @node)
                       :sequence (:sequence msg)
                       :target-id (:target-id msg)
                       :updates (state/pick-piggyback node))
          (negative-ping-req node msg)))
      (catch StatusRuntimeException _
        (negative-ping-req node msg)))))

;;; Probing.

(defn direct-probe
  "Probe `target` (a member map) directly. True on ACK, false on
   deadline/unavailable."
  [node {:keys [id host port]} seq]
  (let [ping (message/msg :ping
                          :sender-id (:id @node)
                          :sequence seq
                          :target-id id
                          :target-host host
                          :target-port port
                          :updates (state/pick-piggyback node))]
    (try
      (let [resp (net/send! host port ping (:ack-timeout-ms (:config @node)))]
        (apply-updates! node (:updates resp))
        (= :ack (:type resp)))
      (catch StatusRuntimeException _ false))))

(defn indirect-probe
  "Ask up to k random live relays to ping `target`. True if any relay ACKs."
  [node {:keys [id host port]} seq]
  (let [k (:k (:config @node))
        relays (membership/random-peers node k)]
    (boolean
     (some (fn [relay]
             (let [req (message/msg :ping-req
                                    :sender-id (:id @node)
                                    :sequence seq
                                    :target-id id
                                    :target-host host
                                    :target-port port
                                    :updates (state/pick-piggyback node))]
               (try
                 (let [resp (net/send! (:host relay) (:port relay) req
                                       (:ack-timeout-ms (:config @node)))]
                   (apply-updates! node (:updates resp))
                   (= :ack (:type resp)))
                 (catch StatusRuntimeException _ false))))
           relays))))

;;; Membership-state transitions (suspicion/confirm/revive).

(defn mark-alive!
  "Revive `member-id` at `incarnation` and disseminate ALIVE."
  [node member-id incarnation]
  (swap! node update :membership
         (fn [m] (-> m
                     (assoc-in [member-id :status] :alive)
                     (assoc-in [member-id :incarnation] incarnation)
                     (update-in [member-id] dissoc :suspected-since))))
  (state/enqueue! node (message/update-entry member-id incarnation :alive)))

(defn declare-suspected
  "Mark `member-id` suspected and disseminate SUSPECT. The suspicion timer
   (:suspected-since) is set only on the first failed probe; repeated failed
   probes of an already-suspected member must not extend it, otherwise a dead
   member would never be confirmed failed (SWIM paper §4.2: one-shot timer)."
  [node member-id incarnation]
  (swap! node update :membership
         (fn [m]
           (let [was-suspected? (= :suspected (get-in m [member-id :status]))]
             (-> m
                 (assoc-in [member-id :status] :suspected)
                 (assoc-in [member-id :suspected-since]
                           (if was-suspected?
                             (get-in m [member-id :suspected-since])
                             (System/currentTimeMillis)))))))
  (state/enqueue! node (message/update-entry member-id incarnation :suspect)))

(defn declare-failed
  "Remove `member-id` and disseminate CONFIRM."
  [node member-id incarnation]
  (membership/remove-member! node member-id)
  (state/enqueue! node (message/update-entry member-id incarnation :confirm)))

(defn sweep-expired-suspicions
  "Declare failed any member whose suspicion has timed out."
  [node]
  (let [{:keys [membership config]} @node
        timeout (:suspicion-timeout-ms config)]
    (doseq [[mid m] membership
            :when (and (= :suspected (:status m))
                       (>= (- (System/currentTimeMillis) (:suspected-since m))
                           timeout))]
      (declare-failed node mid (:incarnation m)))))

;;; Protocol period.

(defn- suspected? [node id]
  (= :suspected (membership/member-status node id)))

(defn run-protocol-period!
  "One SWIM period: sweep suspicions, pick the next target, probe it directly
   then indirectly, and declare suspicion if both fail. A node with
   `:drop-inbound?` set skips the whole period so it neither probes nor
   applies any responses (a fully 'down' node)."
  [node]
  (when-not (:drop-inbound? @node)
    (sweep-expired-suspicions node)
    (swap! node update :sequence inc)
    (when-let [target (membership/round-robin-target node)]
      (let [seq (:sequence @node)
            tid (:id target)]
        (if (direct-probe node target seq)
          (when (suspected? node tid)
            (mark-alive! node tid (get-in @node [:membership tid :incarnation] 0)))
          (if (indirect-probe node target seq)
            (when (suspected? node tid)
              (mark-alive! node tid (get-in @node [:membership tid :incarnation] 0)))
            (declare-suspected node tid (get-in @node [:membership tid :incarnation] 0))))))))

;;; Node lifecycle.

(defn start-loop!
  "Start the daemon protocol-period loop thread; returns the thread."
  [node]
  (let [t (Thread.
           (fn []
             (while (:running? @node)
               (try
                 (run-protocol-period! node)
                 (Thread/sleep (:protocol-period-ms (:config @node)))
                 (catch InterruptedException _
                   nil)  ;; stop-node! interrupts the sleep; exit the loop quietly
                 (catch Exception e
                   (println "SWIM period error:" (.getMessage e)))))))]
    (.setDaemon t true)
    (.start t)
    (swap! node assoc :thread t)
    t))

(defn start-node!
  "Create the node atom, bind its gRPC server, and start the period loop.
   Returns the node atom."
  [config]
  (let [node (state/new-node config)
        server (net/server node)]
    (swap! node assoc :server server)
    (start-loop! node)
    node))

(defn stop-node!
  "Stop the period loop and shut down the gRPC server."
  [node]
  (swap! node assoc :running? false)
  (when-let [t (:thread @node)] (.interrupt t))
  (when-let [s (:server @node)]
    (try (.shutdownNow s) (catch Exception _))))
