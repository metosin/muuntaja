(ns muuntaja.format.charred
  (:refer-clojure :exclude [format])
  (:require
    [charred.api :as charred]
    [muuntaja.format.core :as core])
  (:import
    [charred JSONReader JSONWriter]
    [java.io InputStream InputStreamReader OutputStream OutputStreamWriter]
    [org.apache.commons.io.output ByteArrayOutputStream]))

(defn decoder [options]
  (let [json-reader-fn (charred/json-reader-fn options)]
    (reify
      core/Decode
      (decode [_ data charset]
        (let [[^JSONReader json-rdr finalize-fn] (json-reader-fn)
              input (InputStreamReader. ^InputStream data ^String charset)]
          (with-open [rdr (charred/reader->char-reader input options)]
            (.beginParse json-rdr rdr)
            (finalize-fn (.readObject json-rdr))))))))

(defn encoder [options]
  (let [json-writer-fn (charred/json-writer-fn options)]
    (reify
      core/EncodeToBytes
      (encode-to-bytes [_ data charset]
        (let [output-stream (ByteArrayOutputStream.)
              output        (OutputStreamWriter. output-stream ^String charset)]
          (with-open [^JSONWriter writer (json-writer-fn output)]
            (.writeObject writer data))
          (.toByteArray output-stream)))

      core/EncodeToOutputStream
      (encode-to-output-stream [_ data charset]
        (fn [^OutputStream output-stream]
          (let [output (OutputStreamWriter. output-stream ^String charset)]
            (with-open [^JSONWriter writer (json-writer-fn output)]
              (.writeObject writer data))
            (.flush output-stream)))))))

(def format
  (core/map->Format
    {:name    "application/json"
     :decoder [decoder {:key-fn keyword
                        :async? false}]
     :encoder [encoder]}))
