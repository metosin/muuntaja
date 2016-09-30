(ns muuntaja.formats
  (:require [cheshire.core :as json]
            [cheshire.parse :as parse]
            [clj-yaml.core :as yaml]
            [clojure.tools.reader.edn :as edn]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream DataInputStream DataOutputStream InputStreamReader PushbackReader InputStream ByteArrayInputStream]))

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

;; JSON

(defn make-json-decoder [{:keys [keywords? bigdecimals?]}]
  (if-not bigdecimals?
    (fn [x]
      (if (string? x)
        (json/parse-string x keywords?)
        (json/parse-stream (InputStreamReader. x) keywords?)))
    (fn [x]
      (binding [parse/*use-bigdecimals?* bigdecimals?]
        (if (string? x)
          (json/parse-string x keywords?)
          (json/parse-stream (InputStreamReader. x) keywords?))))))

(defn make-json-encoder [options]
  (fn [data] (json/generate-string data options)))

(defprotocol EncodeJson
  (encode-json [this]))

;; msgpack

(defn make-msgpack-decoder [{:keys [keywords?] :as options}]
  (let [transform (if keywords? walk/keywordize-keys identity)]
    (fn [in]
      (with-open [i (io/input-stream (slurp-to-bytes in))]
        (let [data-input (DataInputStream. i)]
          (transform (msgpack/unpack-stream data-input options)))))))

;; TODO: keyword vs strings? better walk
(defn make-msgpack-encoder [options]
  (fn [data]
    (with-open [out-stream (ByteArrayOutputStream.)]
      (let [data-out (DataOutputStream. out-stream)]
        (msgpack/pack-stream (walk/stringify-keys data) data-out) options)
      (ByteArrayInputStream.
        (.toByteArray out-stream)))))

(defprotocol EncodeMsgpack
  (encode-msgpack [this]))

;; YAML

(defn make-yaml-decoder [options]
  (let [options-args (mapcat identity options)]
    (fn [s] (apply yaml/parse-string s options-args))))

(defn make-yaml-encoder [options]
  (let [options-args (mapcat identity options)]
    (fn [data]
      (apply yaml/generate-string data options-args))))

(defprotocol EncodeYaml
  (encode-yaml [this]))

;; EDN

(defn make-edn-decoder [options]
  (let [options (merge {:readers *data-readers*} options)]
    (fn [x]
      (if (string? x)
        (edn/read-string options x)
        (edn/read options (PushbackReader. (InputStreamReader. x)))))))

(defn make-edn-encoder [_]
  (fn [data]
    (pr-str data)))

(defprotocol EncodeEdn
  (encode-edn [this]))

;; TRANSIT

(defn make-transit-decoder
  [type options]
  (fn [in]
    (let [reader (transit/reader in type options)]
      (transit/read reader))))

(defn make-transit-encoder
  [type {:keys [verbose] :as options}]
  (fn [data]
    (let [out (ByteArrayOutputStream.)
          full-type (if (and (= type :json) verbose)
                      :json-verbose
                      type)
          wrt (transit/writer out full-type options)]
      (transit/write wrt data)
      (ByteArrayInputStream.
        (.toByteArray out)))))

(defprotocol EncodeTransitJson
  (encode-transit-json [this]))

(defprotocol EncodeTransitMessagePack
  (encode-transit-msgpack [this]))
