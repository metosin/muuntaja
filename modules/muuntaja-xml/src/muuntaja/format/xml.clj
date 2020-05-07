(ns muuntaja.format.xml
  (:refer-clojure :exclude [format])
  (:require [clojure.data.xml :as data.xml]
            [muuntaja.format.core :as core])
  (:import (java.io ByteArrayOutputStream
                    DataInputStream
                    DataOutputStream
                    OutputStream
                    OutputStreamWriter)))

(defn- tags-to-element [xml]
  (if (vector? xml)
    (data.xml/sexp-as-element xml)
    xml))

(defn decoder [options]
  (reify
    core/Decode
    (decode [_ data _]
      (data.xml/parse (DataInputStream. data)))))

(defn encoder [options]
  (reify
    core/EncodeToBytes
    (encode-to-bytes [_ data _]
      (let [encoding (:encoding options)]
        (with-open [out-stream (ByteArrayOutputStream.)
                    data-out (DataOutputStream. out-stream)
                    w (OutputStreamWriter. data-out encoding)]
          (let [tags (tags-to-element data)]
            (data.xml/emit tags w :encoding encoding)
            (.flush w)
            (.toByteArray out-stream)))))
    core/EncodeToOutputStream
    (encode-to-output-stream [_ data _]
      (fn [^OutputStream output-stream]
        (let [encoding (:encoding options)
              data-out (DataOutputStream. output-stream)
              w (OutputStreamWriter. data-out encoding)
              tags (tags-to-element data)]
          (data.xml/emit tags w :encoding encoding))))))

(def format
  (core/map->Format
   {:name "application/xml"
    :decoder [decoder]
    :encoder [encoder {:encoding "UTF-8"}]}))

