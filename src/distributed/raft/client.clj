(ns distributed.raft.client
  (:require [clojure.string :as str]
            [distributed.raft.protocol :refer [->clj]]
            [distributed.raft.rpc :as rpc]))

(defn- connect [leader-id]
  (let [[host port] (str/split leader-id #":")]
    (rpc/client host (Integer/parseInt port))))

(defn submit-command
  "Submit `command` to the Raft cluster, following leader redirects and
   retrying `first-peer` while no leader is known or a connection fails.

   `first-peer` is any known node's host:port string. Returns the
   SubmitCommandResponse as a Clojure map with :success, :leader-id,
   :committed, and :result."
  [first-peer command & {:keys [max-retries retry-sleep-ms]
                         :or {max-retries 10 retry-sleep-ms 150}}]
  (loop [peer first-peer
         tries 0]
    (let [resp (try
                 (-> (connect peer)
                     (.submitCommandRPC (rpc/build-submit-request command))
                     ->clj)
                 (catch Exception e
                   {:success false :leader-id first-peer :committed false :result (.getMessage e)}))]
      (if (or (:success resp)
              (>= tries max-retries))
        resp
        (let [next-peer (if (str/blank? (:leader-id resp))
                          first-peer
                          (:leader-id resp))]
          (Thread/sleep retry-sleep-ms)
          (recur next-peer (inc tries)))))))
