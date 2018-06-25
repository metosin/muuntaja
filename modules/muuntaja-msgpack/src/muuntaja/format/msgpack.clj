(ns muuntaja.format.msgpack
  (:refer-clojure :exclude [format])
  (:require [clojure.walk :as walk]
            [msgpack.core :as msgpack]
            [msgpack.clojure-extensions]
            [muuntaja.format.core :as core])
  (:import (java.io ByteArrayOutputStream DataInputStream DataOutputStream OutputStream)))

(defn decoder [{:keys [keywords?] :as options}]
  (let [transform (if keywords? walk/keywordize-keys identity)]
    (reify
      core/Decode
      (decode [_ data _]
        (transform (msgpack/unpack-stream (DataInputStream. data) options))))))

(defn encoder [options]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data _]
      (with-open [out-stream (ByteArrayOutputStream.)]
        (let [data-out (DataOutputStream. out-stream)]
          (msgpack/pack-stream (walk/stringify-keys data) data-out) options)
        (.toByteArray out-stream)))
    core/EncodeToOutputStream
    (encode-to-output-stream [_ data _]
      (fn [^OutputStream output-stream]
        (let [data-out (DataOutputStream. output-stream)]
          (msgpack/pack-stream (walk/stringify-keys data) data-out) options)))))

(def format
  (core/map->Format
    {:name "application/msgpack"
     :decoder [decoder {:keywords? true}]
     :encoder [encoder]}))
