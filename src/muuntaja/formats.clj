(ns muuntaja.formats
  (:require [cheshire.core :as json]
            [cheshire.parse :as parse]
            [clj-yaml.core :as yaml]
            [clojure.tools.reader.edn :as edn]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack]
            [clojure.java.io :as io]
            [muuntaja.protocols :as protocols])
  (:import (java.io ByteArrayOutputStream DataInputStream DataOutputStream InputStreamReader PushbackReader InputStream ByteArrayInputStream OutputStreamWriter OutputStream)))

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
    (fn [x ^String charset]
      (if (string? x)
        (json/parse-string x keywords?)
        (json/parse-stream (InputStreamReader. ^InputStream x charset) keywords?)))
    (fn [x ^String charset]
      (binding [parse/*use-bigdecimals?* bigdecimals?]
        (if (string? x)
          (json/parse-string x keywords?)
          (json/parse-stream (InputStreamReader. ^InputStream x charset) keywords?))))))

(defn make-json-encoder [options]
  (fn [data ^String charset]
    (ByteArrayInputStream. (.getBytes (json/generate-string data options) charset))))

(defn make-json-string-encoder [options]
  (fn [data _]
    (json/generate-string data options)))

(defn make-streaming-json-encoder [options]
  (fn [data ^String charset]
    (protocols/->StreamableResponse
      (fn [^OutputStream output-stream]
        (json/generate-stream
          data
          (OutputStreamWriter. output-stream charset)
          options)
        (.flush output-stream)))))

(defprotocol EncodeJson
  (encode-json [this]))

;; msgpack

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

(defprotocol EncodeMsgpack
  (encode-msgpack [this]))

;; YAML

;; TODO: read stream + charset
(defn make-yaml-decoder [options]
  (let [options-args (mapcat identity options)]
    (fn [s _] (apply yaml/parse-string s options-args))))

(defn make-yaml-encoder [options]
  (let [options-args (mapcat identity options)]
    (fn [data ^String charset]
      (ByteArrayInputStream.
        (.getBytes
          ^String (apply yaml/generate-string data options-args)
          charset)))))

(defprotocol EncodeYaml
  (encode-yaml [this]))

;; EDN

(defn make-edn-decoder [options]
  (let [options (merge {:readers *data-readers*} options)]
    (fn [x ^String charset]
      (if (string? x)
        (edn/read-string options x)
        (edn/read options (PushbackReader. (InputStreamReader. ^InputStream x charset)))))))

(defn make-edn-encoder [_]
  (fn [data ^String charset]
    (ByteArrayInputStream.
      (.getBytes
        (pr-str data)
        charset))))

(defn make-edn-string-encoder [_]
  (fn [data _]
    (pr-str data)))

(defprotocol EncodeEdn
  (encode-edn [this]))

;; TRANSIT

;; TODO: charset
(defn make-transit-decoder
  [type options]
  (fn [in _]
    (let [reader (transit/reader in type options)]
      (transit/read reader))))

(defn make-transit-encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (fn [data _]
      (let [baos (ByteArrayOutputStream.)
            writer (transit/writer baos full-type options)]
        (transit/write writer data)
        (ByteArrayInputStream.
          (.toByteArray baos))))))

(defn make-streaming-transit-encoder [type {:keys [verbose] :as options}]
  (let [full-type (if (and (= type :json) verbose) :json-verbose type)]
    (fn [data _]
      (protocols/->StreamableResponse
        (fn [^OutputStream output-stream]
          (transit/write
            (transit/writer output-stream full-type options) data)
          (.flush output-stream))))))

(defprotocol EncodeTransitJson
  (encode-transit-json [this]))

(defprotocol EncodeTransitMessagePack
  (encode-transit-msgpack [this]))
