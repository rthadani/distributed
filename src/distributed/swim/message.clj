(ns distributed.swim.message)

(comment
  (gloss/defcodec message-type (codec/enum :byte :ping :ack :update :check))
  (gloss/defcodec status (codec/enum :byte :up :down :unknown))
  (gloss/defcodec id (gloss/string :utf-8))
  (gloss/defcodec membership (codec/ordered-map :id id :status status))
  (gloss/defcodec member-list (gloss/repeated membership))

  (gloss/defcodec ping (codec/ordered-map :type :ping :id id :member-list member-list))
  (gloss/defcodec ack (codec/ordered-map :type :ack :id id :member-list member-list))
  (gloss/defcodec check (codec/ordered-map :type :check :id id :destination id))
  (gloss/defcodec update-list (codec/ordered-map :type :update :id id :member-list member-list))

  (gloss/defcodec swim-message
                  (gloss/header
                    message-type
                    {:ping ping :ack ack :check check :update update}
                    :type)))
;messages

(defn tcp-peer
  [id host port]
  {:type :tcp
   :id   id
   :host host
   :port port})

(defn channel-peer
  [id]
  {:id   id
   :type :channel})

(defn ping-ack-msg
  [type sender-id member-list]
  {:type        type
   :id          (str sender-id)
   :member-list member-list})

(defn update-message
  [sender-id member-list]
  {:type        :update
   :id          (str sender-id)
   :member-list member-list})

(defn indirect-ping
  [sender-id destination-id host port]
  {:type :indirect-ping
   :id   (str sender-id)
   :host host
   :port port})

(defn indirect-ping-response
  [sender-id])

