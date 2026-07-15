(ns org.greamples.epmap.queue
  "Collects captured minimap chunk pixel data and periodically drains it in
   batches. Keys use the format \"dimension:chunkX:chunkZ\"
   (e.g. \"minecraft:overworld:10:-5\")."
  (:import [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]))

(def ^:private ^:const flush-interval-ms 3000)

;; Queued chunks awaiting the next flush.
(defonce ^:private basket (atom {}))

;; The single scheduled worker, or nil when stopped.
(defonce ^:private scheduler (atom nil))

;; Consumer invoked with each drained batch (a map of key -> int[]).
;; The transport layer must reset! this to receive data; while nil, drained
;; batches are discarded.
(defonce consumer (atom nil))

(defn set-consumer!
  "Sets the function invoked with each drained batch, or nil to disable."
  [f]
  (reset! consumer f))

(defn add-chunk
  "Queues a chunk's ARGB pixel data under its dimension/coordinate key."
  [dimension chunk-x chunk-z pixel-data]
  (swap! basket assoc (str dimension ":" chunk-x ":" chunk-z) pixel-data)
  nil)

(defn flush!
  "Atomically drains the queued chunks and hands them to the consumer.
   swap-vals! swaps in an empty map and returns the previous contents in one
   step, so chunks added concurrently during the drain are never lost."
  []
  (let [[queued _] (swap-vals! basket (constantly {}))]
    (when (seq queued)
      (when-let [c @consumer]
        (c queued))))
  nil)

(defn start-worker!
  "Starts the periodic flush worker. Idempotent: a second call while running is
   a no-op."
  []
  (locking scheduler
    (when (nil? @scheduler)
      (let [factory (reify ThreadFactory
                      (newThread [_ r]
                        (doto (Thread. r "EPMap-Queue-Worker")
                          (.setDaemon true))))
            ^ScheduledExecutorService ex (Executors/newSingleThreadScheduledExecutor factory)]
        (.scheduleAtFixedRate ex ^Runnable (fn [] (flush!))
                              flush-interval-ms flush-interval-ms TimeUnit/MILLISECONDS)
        (reset! scheduler ex))))
  nil)

(defn stop-worker!
  "Stops the flush worker and releases its thread."
  []
  (locking scheduler
    (when-let [^ScheduledExecutorService ex @scheduler]
      (.shutdownNow ex)
      (reset! scheduler nil)))
  nil)
