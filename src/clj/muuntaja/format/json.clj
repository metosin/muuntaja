(ns muuntaja.format.json
  (:require [cheshire.core :as cheshire]
            [cheshire.parse :as parse]
            [muuntaja.protocols :as protocols])
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

;;
;; format
;;

;; type

(def json-type "application/json")

;; formats

(def json-format
  {:decoder [make-json-decoder {:key-fn true}]
   :encoder [make-json-encoder]})

(def streaming-json-format
  (assoc json-format :encoder [make-streaming-json-encoder]))

;; options

(defn with-json-format [options]
  (assoc-in options [:formats json-type] json-format))

(defn with-streaming-json-format [options]
  (assoc-in options [:formats json-type] streaming-json-format))
