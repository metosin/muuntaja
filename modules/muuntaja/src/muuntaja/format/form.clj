(ns muuntaja.format.form
  (:refer-clojure :exclude [format])
  (:require [muuntaja.format.core :as core]
            [ring.util.codec :as codec])
  (:import (java.io OutputStream)))

(defn decoder
  "Create a decoder which converts a ‘application/x-www-form-urlencoded’
  representation into clojure data."
  [_]
  (reify
    core/Decode
    (decode [_ data charset]
      (let [input (slurp data :encoding charset)]
        (codec/form-decode input charset)))))

(defn encoder
  "Create an encoder which converts clojure data into an
  ‘application/x-www-form-urlencoded’ representation."
  [_]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (let [encoded (codec/form-encode data charset)]
        (.getBytes encoded ^String charset)))

    core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^OutputStream output-stream]
        (let [encoded (codec/form-encode data charset)
              bytes (.getBytes encoded ^String charset)]
          (.write output-stream bytes))))))

(def format
  "Formatter handling ‘application/x-www-form-urlencoded’ representations
  with the `ring.util.codec` library."
  (core/map->Format
    {:name "application/x-www-form-urlencoded"
     :decoder [decoder]
     :encoder [encoder]}))
