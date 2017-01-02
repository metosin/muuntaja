(ns muuntaja.format.transit
  (:require [cognitect.transit :as transit]
            [muuntaja.protocols :as protocols]
            [msgpack.clojure-extensions])
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
        (ByteArrayInputStream.
          (.toByteArray baos))))))

(defn make-streaming-transit-encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (fn [data _]
      (protocols/->StreamableResponse
        (fn [^OutputStream output-stream]
          (transit/write
            (transit/writer output-stream full-type options) data)
          (.flush output-stream))))))

(defprotocol EncodeTransitJson
  (encode-transit-json [this charset]))

(defprotocol EncodeTransitMessagePack
  (encode-transit-msgpack [this charset]))

;;
;; formats
;;

(def transit-json-type "application/transit+json")

(def transit-json-format
  {:decoder [(partial make-transit-decoder :json)]
   :encoder [(partial make-transit-encoder :json)]
   :encode-protocol [EncodeTransitJson encode-transit-json]})

(def transit-msgpack-type "application/transit+msgpack")

(def transit-msgpack-format
  {:decoder [(partial make-transit-decoder :msgpack)]
   :encoder [(partial make-transit-encoder :msgpack)]
   :encode-protocol [EncodeTransitMessagePack encode-transit-msgpack]})

