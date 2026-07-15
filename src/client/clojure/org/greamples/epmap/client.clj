(ns org.greamples.epmap.client
  "Fabric client entrypoint. Detects the target server and drives the chunk
   queue worker's lifecycle."
  (:require [org.greamples.epmap.queue :as queue]
            [clojure.string :as str])
  (:import [net.fabricmc.fabric.api.client.networking.v1 ClientPlayConnectionEvents
            ClientPlayConnectionEvents$Join ClientPlayConnectionEvents$Disconnect])
  (:gen-class
    :name org.greamples.epmap.client.EpMapClient
    :implements [net.fabricmc.api.ClientModInitializer]))

(def ^:const server-ip "epserv.ru")

;; Whether the client is currently connected to the target server. Written on
;; the connection callback thread, read from the mixin thread; an atom gives
;; the needed visibility across threads.
(defonce ^:private connected-to-target? (atom false))

(defn matches-target-server?
  "Matches the target server by host, ignoring any port suffix. Uses exact host
   or subdomain matching so look-alike hosts (e.g. \"epserv.ru.evil.com\") do
   not match."
  [address]
  (if (str/blank? address)
    false
    (let [host (-> address (str/split #":") first str/trim str/lower-case)]
      (or (= host server-ip)
          (str/ends-with? host (str "." server-ip))))))

(defn target-server?
  "Called from the mixin: true while connected to the target server."
  []
  @connected-to-target?)

(defn add-chunk
  "Called from the mixin: queues a chunk's ARGB pixel data."
  [dimension chunk-x chunk-z ^ints pixel-data]
  (queue/add-chunk dimension chunk-x chunk-z pixel-data))

(defn -onInitializeClient [_this]
  (queue/start-worker!)
  (.register ClientPlayConnectionEvents/JOIN
             (reify ClientPlayConnectionEvents$Join
               (onPlayReady [_ _handler _sender client]
                 (reset! connected-to-target?
                         (matches-target-server?
                           (some-> (.getCurrentServer client) (.-ip)))))))
  (.register ClientPlayConnectionEvents/DISCONNECT
             (reify ClientPlayConnectionEvents$Disconnect
               (onPlayDisconnect [_ _handler _client]
                 (reset! connected-to-target? false))))
  nil)
