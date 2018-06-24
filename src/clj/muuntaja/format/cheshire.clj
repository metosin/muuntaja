(ns muuntaja.format.cheshire
  (:refer-clojure :exclude [format])
  (:require [cheshire.core :as cheshire]
            [cheshire.parse :as parse]
            [muuntaja.format.core :as core])
  (:import (java.io InputStreamReader InputStream OutputStreamWriter OutputStream)))

(defn decoder [{:keys [key-fn array-coerce-fn bigdecimals?]}]
  (if-not bigdecimals?
    (reify
      core/Decode
      (decode [_ data charset]
        (cheshire/parse-stream (InputStreamReader. ^InputStream data ^String charset) key-fn array-coerce-fn)))
    (reify
      core/Decode
      (decode [_ data charset]
        (binding [parse/*use-bigdecimals?* bigdecimals?]
          (cheshire/parse-stream (InputStreamReader. ^InputStream data ^String charset) key-fn array-coerce-fn))))))

(defn encoder [options]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes (cheshire/generate-string data options) ^String charset))
    core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^OutputStream output-stream]
        (cheshire/generate-stream
          data (OutputStreamWriter. output-stream ^String charset) options)
        (.flush output-stream)))))

(def format
  (core/map->Format
    {:name "application/json"
     :decoder [decoder {:key-fn true}]
     :encoder [encoder]}))
