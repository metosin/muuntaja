(ns muuntaja.format.jsonista
  (:require [jsonista.core :as jsonista]
            [muuntaja.protocols :as protocols])
  (:import (java.io ByteArrayInputStream
                    InputStream
                    InputStreamReader
                    OutputStreamWriter
                    OutputStream)))

(defn ^:no-doc make-json-decoder [{:keys [keywords?]}]
  (let [mapper (jsonista/make-mapper {:keywordize? keywords?})]
    (fn [x ^String charset]
      (if (string? x)
        (jsonista/from-json x mapper)
        (jsonista/from-json (InputStreamReader. ^InputStream x charset) mapper)))))

(defn ^:no-doc make-json-encoder [options]
  (fn [data ^String charset]
    (ByteArrayInputStream. (.getBytes (jsonista/to-json data) charset))))

(defn make-streaming-json-encoder [{:keys [keywords?]}]
  (let [mapper (jsonista/make-mapper {:keywordize? keywords?})]
    (fn [data ^String charset]
      (protocols/->StreamableResponse
       (fn [^OutputStream output-stream]
         (jsonista/write-to
          data
          (OutputStreamWriter. output-stream charset)
          mapper))))))

;;;
;;; format
;;;

;; type

(def json-type "application/json")

;; formats

(def json-format
  {:decoder [make-json-decoder {:keywords? true}]
   :encoder [make-json-encoder]})

(def streaming-json-format
  (assoc json-format :encoder [make-streaming-json-encoder]))

;; options

(defn with-json-format [options]
  (assoc-in options [:formats json-type] json-format))

(defn with-streaming-json-format [options]
  (assoc-in options [:formats json-type] streaming-json-format))
