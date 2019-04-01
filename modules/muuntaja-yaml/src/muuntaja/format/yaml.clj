(ns muuntaja.format.yaml
  (:refer-clojure :exclude [format])
  (:require [clj-yaml.core :as yaml]
            [muuntaja.format.core :as core])
  (:import (java.io OutputStream OutputStreamWriter InputStream)
           (org.yaml.snakeyaml Yaml)))

(defn decoder [{:keys [unsafe mark keywords] :or {keywords true}}]
  (reify
    core/Decode
    (decode [_ data _]
      ;; Call SnakeYAML .load directly because clj-yaml only provides String version
      (yaml/decode (.load (yaml/make-yaml :unsafe unsafe :mark mark) ^InputStream data) keywords))))

(defn encoder [options]
  (let [options-args (mapcat identity options)]
    (reify
      core/EncodeToBytes
      (encode-to-bytes [_ data _]
        (.getBytes
          ^String (apply yaml/generate-string data options-args)))
      core/EncodeToOutputStream
      (encode-to-output-stream [_ data _]
        (fn [^OutputStream output-stream]
          (.dump ^Yaml (apply yaml/make-yaml options-args) (yaml/encode data) (OutputStreamWriter. output-stream))
          (.flush output-stream))))))

(def format
  (core/map->Format
    {:name "application/x-yaml"
     :decoder [decoder {:keywords true}]
     :encoder [encoder]}))
