(ns distributed.raft.main
  (:require [distributed.raft.rpc :as rpc]
            [distributed.raft.state :as state]))

(defn node-config
  "Build a node configuration. `port` is the gRPC listen port. Nodes identify
   themselves by their host:port string (stored as :me). `:state-file` defaults
   to data/raft-<port>.edn; pass nil to disable persistence."
  [port & {:keys [servers election-timeout-ms heartbeat-ms state-file]}]
  (let [me (str "127.0.0.1:" port)]
    {:me me
     :port port
     :servers (or servers #{"127.0.0.1:8000" "127.0.0.1:8001" "127.0.0.1:8002"})
     :election-timeout-ms (or election-timeout-ms 300)
     :heartbeat-ms (or heartbeat-ms 50)
     :state-file (or state-file (str "data/raft-" port ".edn"))}))

(defn start-node!
  "Start a Raft node: creates its state atom, binds the gRPC server, and enters
   the Follower state. Returns {:server :global-state :config}."
  [config]
  (let [global-state (state/new-node-state config)
        server (rpc/server (:port config) global-state)]
    (state/change-state :Follower global-state config)
    {:server server :global-state global-state :config config}))

(defn stop-node!
  "Stop a node's gRPC server and shut down its timers/executors."
  [{:keys [server global-state]}]
  (state/cancel-election-timer! global-state)
  (state/stop-heartbeat! global-state)
  (when-let [s (:scheduler @global-state)] (.shutdownNow s))
  (when-let [e (:rpc-executor @global-state)] (.shutdownNow e))
  (when server (.shutdownNow server)))
