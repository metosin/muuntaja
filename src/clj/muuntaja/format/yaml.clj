(ns muuntaja.format.yaml
  "Requires [circleci/clj-yaml \"0.5.5\"] as dependency"
  (:require [clj-yaml.core :as yaml])
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

;;
;; format
;;

(def yaml-type "application/x-yaml")

(def yaml-format
  {:decoder [make-yaml-decoder {:keywords true}]
   :encoder [make-yaml-encoder]})

(defn with-yaml-format [options]
  (assoc-in options [:formats yaml-type] yaml-format))
