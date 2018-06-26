(ns muuntaja.format.json
  (:refer-clojure :exclude [format])
  (:require [jsonista.core :as j]
            [muuntaja.format.core :as core])
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

(defn decoder [options]
  (let [mapper (object-mapper! options)]
    (reify
      core/Decode
      (decode [_ data charset]
        (if (.equals "utf-8" ^String charset)
          (j/read-value data mapper)
          (j/read-value (InputStreamReader. ^InputStream data ^String charset) mapper))))))

(defn encoder [options]
  (let [mapper (object-mapper! options)]
    (reify
      core/EncodeToBytes
      (encode-to-bytes [_ data charset]
        (if (.equals "utf-8" ^String charset)
          (j/write-value-as-bytes data mapper)
          (.getBytes ^String (j/write-value-as-string data mapper) ^String charset)))
      core/EncodeToOutputStream
      (encode-to-output-stream [_ data charset]
        (fn [^OutputStream output-stream]
          (if (.equals "utf-8" ^String charset)
            (j/write-value output-stream data mapper)
            (j/write-value (OutputStreamWriter. output-stream ^String charset) data mapper)))))))

(def format
  (core/map->Format
    {:name "application/json"
     :decoder [decoder {:decode-key-fn true}]
     :encoder [encoder]}))
