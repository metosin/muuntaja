(ns muuntaja.format.yaml
  (:refer-clojure :exclude [format])
  (:require [clj-yaml.core :as yaml]
            [muuntaja.format.core :as core])
  (:import (java.io OutputStream)))

(defn decoder [options]
  (let [options-args (mapcat identity options)]
    (reify
      core/Decode
      (decode [_ data charset]
        (apply yaml/parse-string (slurp data :encoding charset) options-args)))))

(defn encoder [options]
  (let [options-args (mapcat identity options)]
    (reify
      core/EncodeToBytes
      (encode-to-bytes [_ data charset]
        (.getBytes
          ^String (apply yaml/generate-string data options-args)
          ^String charset))
      core/EncodeToOutputStream
      (encode-to-output-stream [_ data charset]
        (fn [^OutputStream output-stream]
          (.write output-stream (.getBytes ^String (apply yaml/generate-string data options-args)
                                           ^String charset)))))))

(def format
  (core/map->Format
    {:name "application/x-yaml"
     :decoder [decoder {:keywords true}]
     :encoder [encoder]}))
