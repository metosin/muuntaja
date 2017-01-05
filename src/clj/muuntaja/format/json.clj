(ns muuntaja.format.json
  (:require [cheshire.core :as cheshire]
            [cheshire.parse :as parse]
            [muuntaja.protocols :as protocols]
            [msgpack.clojure-extensions]
            [muuntaja.json :as json])
  (:import (java.io InputStreamReader InputStream ByteArrayInputStream OutputStreamWriter OutputStream)))

(defn make-json-decoder [{:keys [key-fn array-coerce-fn bigdecimals?]}]
  (if-not bigdecimals?
    (fn [x ^String charset]
      (if (string? x)
        (cheshire/parse-string x key-fn array-coerce-fn)
        (cheshire/parse-stream (InputStreamReader. ^InputStream x charset) key-fn array-coerce-fn)))
    (fn [x ^String charset]
      (binding [parse/*use-bigdecimals?* bigdecimals?]
        (if (string? x)
          (cheshire/parse-string x key-fn array-coerce-fn)
          (cheshire/parse-stream (InputStreamReader. ^InputStream x charset) key-fn array-coerce-fn))))))

(defn make-json-encoder [options]
  (fn [data ^String charset]
    (ByteArrayInputStream. (.getBytes (cheshire/generate-string data options) charset))))

(defn make-json-string-encoder [options]
  (fn [data _]
    (cheshire/generate-string data options)))

(defn make-streaming-json-encoder [options]
  (fn [data ^String charset]
    (protocols/->StreamableResponse
      (fn [^OutputStream output-stream]
        (cheshire/generate-stream
          data
          (OutputStreamWriter. output-stream charset)
          options)
        (.flush output-stream)))))

(defprotocol EncodeJson
  (encode-json [this charset]))

;; muuntaja.json - experimental

(defn ^:no-doc make-muuntaja-json-decoder [{:keys [keywords?]}]
  (let [mapper (json/make-mapper {:keywordize? keywords?})]
    (fn [x ^String charset]
      (json/from-json x mapper))))

(defn ^:no-doc make-muuntaja-json-encoder [options]
  (fn [data ^String charset]
    (ByteArrayInputStream. (.getBytes (json/to-json data) charset))))

(defn make-streaming-muuntaja-json-encoder [{:keys [keywords?]}]
  (let [mapper (json/make-mapper {:keywordize? keywords?})]
    (fn [data ^String charset]
      (protocols/->StreamableResponse
        (fn [^OutputStream output-stream]
          (json/write-to
            data
            (OutputStreamWriter. output-stream charset)
            mapper))))))

;;
;; format
;;

(def json-type "application/json")

(def json-format
  {:decoder [make-json-decoder {:key-fn true}]
   :encoder [make-json-encoder]
   :encode-protocol [EncodeJson encode-json]})

(def streaming-json-format
  (assoc json-format :encoder [make-streaming-json-encoder]))

(def muuntaja-json-format
  {:decoder [make-muuntaja-json-decoder {:keywords? true}]
   :encoder [make-muuntaja-json-encoder]
   :encode-protocol [EncodeJson encode-json]})

(def streaming-muuntaja-json-format
  (assoc muuntaja-json-format :encoder [make-streaming-muuntaja-json-encoder]))
