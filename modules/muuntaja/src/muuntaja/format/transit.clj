(ns muuntaja.format.transit
  (:require [cognitect.transit :as transit]
            [muuntaja.format.core :as core])
  (:import (java.io ByteArrayOutputStream OutputStream)))

(defn decoder
  [type options]
  (reify
    core/Decode
    (decode [_ data _]
      (let [reader (transit/reader data type options)]
        (transit/read reader)))))

(defn encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (reify
      core/EncodeToBytes
      (encode-to-bytes [_ data _]
        (let [baos (ByteArrayOutputStream.)
              writer (transit/writer baos full-type options)]
          (transit/write writer data)
          (.toByteArray baos)))
      core/EncodeToOutputStream
      (encode-to-output-stream [_ data _]
        (fn [^OutputStream output-stream]
          (transit/write
            (transit/writer output-stream full-type options) data)
          (.flush output-stream))))))

(def json-format
  (core/map->Format
    {:name "application/transit+json"
     :decoder [(partial decoder :json)]
     :encoder [(partial encoder :json)]}))

(def msgpack-format
  (core/map->Format
    {:name "application/transit+msgpack"
     :decoder [(partial decoder :msgpack)]
     :encoder [(partial encoder :msgpack)]}))
