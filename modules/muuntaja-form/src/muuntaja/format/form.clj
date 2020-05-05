(ns muuntaja.format.form
  (:refer-clojure :exclude [format])
  (:require [muuntaja.format.core :as core]
            [ring.util.codec :as codec])
  (:import (java.io OutputStream)))

(defn- map-keys [f coll]
  (->> (map (fn [[k v]] [(f k) v]) coll) (into {})))

(defn decoder
  "Create a decoder which converts a ‘application/x-www-form-urlencoded’
  representation into clojure data."
  [{:keys [decode-key-fn] :as options}]
  (reify
    core/Decode
    (decode [_ data charset]
      (let [input (slurp data :encoding charset)
            output (codec/form-decode input charset)]
        (if decode-key-fn
          (map-keys decode-key-fn output)
          output)))))

(defn encoder
  "Create an encoder which converts clojure data into an
  ‘application/x-www-form-urlencoded’ representation."
  [_]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (let [encoded (codec/form-encode data charset)]
        (.getBytes ^String encoded ^String charset)))

    core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^OutputStream output-stream]
        (let [encoded (codec/form-encode data charset)
              bytes (.getBytes ^String encoded ^String charset)]
          (.write output-stream bytes))))))

(def format
  "Formatter handling ‘application/x-www-form-urlencoded’ representations
  with the `ring.util.codec` library."
  (core/map->Format
    {:name "application/x-www-form-urlencoded"
     :decoder [decoder {:decode-key-fn keyword}]
     :encoder [encoder]}))
