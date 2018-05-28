(ns muuntaja.format.transit
  (:require [cognitect.transit :as transit]
            [muuntaja.protocols :as protocols])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream OutputStream)))

;; uses default charset)

(defn make-transit-decoder
  [type options]
  (fn [in _]
    (let [reader (transit/reader in type options)]
      (transit/read reader))))

(defn make-transit-encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (fn [data _]
      (let [baos (ByteArrayOutputStream.)
            writer (transit/writer baos full-type options)]
        (transit/write writer data)
        (protocols/->ByteResponse
          (.toByteArray baos))))))

(defn make-streaming-transit-encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (fn [data _]
      (protocols/->StreamableResponse
        (fn [^OutputStream output-stream]
          (transit/write
            (transit/writer output-stream full-type options) data)
          (.flush output-stream))))))

;;
;; formats
;;

(def transit-json-type "application/transit+json")

(def transit-json-format
  {:decoder [(partial make-transit-decoder :json)]
   :encoder [(partial make-transit-encoder :json)]})

(defn with-transit-json-format [options]
  (assoc-in options [:formats transit-json-type] transit-json-format))

(def streaming-transit-json-format
  (assoc transit-json-format :encoder [(partial make-streaming-transit-encoder :json)]))

(defn with-streaming-transit-json-format [options]
  (assoc-in options [:formats transit-json-type] streaming-transit-json-format))

(def transit-msgpack-type "application/transit+msgpack")

(def transit-msgpack-format
  {:decoder [(partial make-transit-decoder :msgpack)]
   :encoder [(partial make-transit-encoder :msgpack)]})

(defn with-transit-msgpack-format [options]
  (assoc-in options [:formats transit-msgpack-type] transit-msgpack-format))

(def streaming-transit-msgpack-format
  (assoc transit-msgpack-format :encoder [(partial make-streaming-transit-encoder :msgpack)]))

(defn with-streaming-transit-msgpack-format [options]
  (assoc-in options [:formats transit-msgpack-type] streaming-transit-msgpack-format))
