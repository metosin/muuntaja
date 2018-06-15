(ns muuntaja.format.json
  (:require [jsonista.core :as j]
            [muuntaja.protocols :as protocols])
  (:import (java.io InputStream
                    InputStreamReader
                    OutputStreamWriter
                    OutputStream)
           (com.fasterxml.jackson.databind ObjectMapper)))

(defn object-mapper! [{:keys [mapper] :as options}]
  (cond
    (instance? ObjectMapper mapper)
    mapper

    (or (contains? options :key-fn) (contains? options :bigdecimals?))
    (throw (AssertionError.
             (str
               "In Muuntaja 0.6.0+ the default JSON formatter has changed\n"
               "from Cheshire to Jsonita. Changed options:\n\n"
               "  :key-fn       => :encode-key-fn & :decode-key-fn\n"
               "  :bigdecimals? => :bigdecimals\n"
               options "\n")))

    :else
    (j/object-mapper (dissoc options :mapper))))

(defn make-json-decoder [options]
  (let [mapper (object-mapper! options)]
    (fn [x ^String charset]
      (if (string? x)
        (j/read-value x mapper)
        (if (.equals "utf-8" charset)
          (j/read-value x mapper)
          (j/read-value (InputStreamReader. ^InputStream x charset) mapper))))))

(defn make-json-encoder [options]
  (let [mapper (object-mapper! options)]
    (fn [data ^String charset]
      (protocols/->ByteResponse
        (if (.equals "utf-8" charset)
          (j/write-value-as-bytes data mapper)
          (.getBytes ^String (j/write-value-as-string data mapper) charset))))))

(defn make-streaming-json-encoder [options]
  (let [mapper (object-mapper! options)]
    (fn [data ^String charset]
      (protocols/->StreamableResponse
        (fn [^OutputStream output-stream]
          (if (.equals "utf-8" charset)
            (j/write-value output-stream data mapper)
            (j/write-value (OutputStreamWriter. output-stream charset) data mapper)))))))

;;;
;;; format
;;;

;; type

(def json-type "application/json")

;; formats

(def json-format
  {:decoder [make-json-decoder {:decode-key-fn true}]
   :encoder [make-json-encoder]})

(def streaming-json-format
  (assoc json-format :encoder [make-streaming-json-encoder]))

;; options

(defn with-json-format [options]
  (assoc-in options [:formats json-type] json-format))

(defn with-streaming-json-format [options]
  (assoc-in options [:formats json-type] streaming-json-format))
