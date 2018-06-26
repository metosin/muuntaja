(ns muuntaja.format.edn
  (:refer-clojure :exclude [format])
  (:require [clojure.edn :as edn]
            [muuntaja.format.core :as core])
  (:import (java.io InputStreamReader PushbackReader InputStream OutputStream)))

(defn decoder [options]
  (let [options (merge {:readers *data-readers*} options)]
    (reify
      core/Decode
      (decode [_ data charset]
        (edn/read options (PushbackReader. (InputStreamReader. ^InputStream data ^String charset)))))))

(defn encoder [_]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes
        (pr-str data)
        ^String charset))
    core/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^OutputStream output-stream]
        (.write output-stream (.getBytes
                                (pr-str data)
                                ^String charset))))))

(def format
  (core/map->Format
    {:name "application/edn"
     :decoder [decoder]
     :encoder [encoder]}))
