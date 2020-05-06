(ns muuntaja.format.xml
  (:refer-clojure :exclude [format])
  (:require [clojure.data.xml :as data.xml]
            [clojure.java.io :as io]
            [muuntaja.format.core :as core])
  (:import (java.io ByteArrayOutputStream DataInputStream DataOutputStream OutputStream)))

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
      (with-open [out-stream (ByteArrayOutputStream.)]
        (let [data-out (DataOutputStream. out-stream)]
          (with-open [w (io/writer out-stream)]
            (let [tags (tags-to-element data)]
              (data.xml/emit tags w)
              (.toByteArray out-stream))))))
    core/EncodeToOutputStream
    (encode-to-output-stream [_ data _]
      (fn [^OutputStream output-stream]
        (let [data-out (DataOutputStream. output-stream)
              w (io/writer data-out)
              tags (tags-to-element data)]
          (data.xml/emit tags w))))))

(def format
  (core/map->Format
   {:name "application/xml"
    :decoder [decoder]
    :encoder [encoder]}))

