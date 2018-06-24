(ns muuntaja.format.yaml
  "Requires [circleci/clj-yaml \"0.5.5\"] as dependency"
  (:refer-clojure :exclude [format])
  (:require [clj-yaml.core :as yaml]
            [muuntaja.format.core :as core])
  (:import (java.io OutputStream)))

(defn decoder [options]
  (let [options-args (mapcat identity options)]
    (reify
      core/Decode
      (decode [_ data _]
        (apply yaml/parse-string data options-args)))))

(defn encoder [options]
  (let [options-args (mapcat identity options)]
    (reify
      core/Encode
      (encode [_ data _]
        (.getBytes
          ^String (apply yaml/generate-string data options-args)))
      core/EncodeToStream
      (encode-to-stream [_ data _]
        (fn [^OutputStream output-stream]
          (.write output-stream (.getBytes ^String (apply yaml/generate-string data options-args))))))))

(def format
  (core/map->Format
    {:type "application/x-yaml"
     :decoder [decoder {:keywords true}]
     :encoder [encoder]}))