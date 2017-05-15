(ns muuntaja.format.msgpack
  "Requires [clojure-msgpack \"1.2.0\" :exclusions [org.clojure/clojure]] as dependency"
  (:require [clojure.walk :as walk]
            [msgpack.core :as msgpack]
            [clojure.java.io :as io]
            [msgpack.clojure-extensions])
  (:import (java.io ByteArrayOutputStream DataInputStream DataOutputStream InputStream ByteArrayInputStream)))

(defn- slurp-to-bytes ^bytes [^InputStream in]
  (if in
    (let [buf (byte-array 4096)
          out (ByteArrayOutputStream.)]
      (loop []
        (let [r (.read in buf)]
          (when (not= r -1)
            (.write out buf 0 r)
            (recur))))
      (.toByteArray out))))

;; TODO: charset, better streaming
(defn make-msgpack-decoder [{:keys [keywords?] :as options}]
  (let [transform (if keywords? walk/keywordize-keys identity)]
    (fn [in _]
      (with-open [i (io/input-stream (slurp-to-bytes in))]
        (let [data-input (DataInputStream. i)]
          (transform (msgpack/unpack-stream data-input options)))))))

;; TODO: keyword vs strings? better walk
(defn make-msgpack-encoder [options]
  (fn [data _]
    (with-open [out-stream (ByteArrayOutputStream.)]
      (let [data-out (DataOutputStream. out-stream)]
        (msgpack/pack-stream (walk/stringify-keys data) data-out) options)
      (ByteArrayInputStream.
        (.toByteArray out-stream)))))

;;
;; format
;;

(def msgpack-type "application/msgpack")

(def msgpack-format
  {:decoder [make-msgpack-decoder {:keywords? true}]
   :encoder [make-msgpack-encoder]})

(defn with-msgpack-format [options]
  (assoc-in options [:formats msgpack-type] msgpack-format))
