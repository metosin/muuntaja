(ns muuntaja.format.yaml
  (:require [clj-yaml.core :as yaml]
            [msgpack.clojure-extensions])
  (:import (java.io ByteArrayInputStream)))

;; uses default charset)

(defn make-yaml-decoder [options]
  (let [options-args (mapcat identity options)]
    (fn [in _]
      (apply yaml/parse-string in options-args))))

(defn make-yaml-encoder [options]
  (let [options-args (mapcat identity options)]
    (fn [data ^String _]
      (ByteArrayInputStream.
        (.getBytes
          ^String (apply yaml/generate-string data options-args))))))

(defprotocol EncodeYaml
  (encode-yaml [this charset]))

;;
;; format
;;

(def yaml-type "application/x-yaml")

(def yaml-format
  {:decoder [make-yaml-decoder {:keywords true}]
   :encoder [make-yaml-encoder]
   :encode-protocol [EncodeYaml encode-yaml]})
