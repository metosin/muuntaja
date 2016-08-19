(ns muuntaja.formats
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.tools.reader.edn :as edn]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack]
            [clojure.java.io :as io]
            [muuntaja.util :as util])
  (:import [java.io ByteArrayOutputStream DataInputStream DataOutputStream InputStreamReader]))

(set! *warn-on-reflection* true)

;; JSON

(defn make-json-decoder [{:keys [keywords?]}]
  (fn [x]
    (if (string? x)
      (json/parse-string x keywords?)
      (json/parse-stream (InputStreamReader. x) keywords?))))

(defn make-json-encoder [options]
  (fn [data] (json/generate-string data options)))

(defprotocol EncodeJson
  (encode-json [this]))

;; msgpack

(defn make-msgpack-decoder [options]
  (fn [in]
    (with-open [i (io/input-stream (util/slurp-to-bytes in))]
      (let [data-input (DataInputStream. i)]
        (msgpack/unpack-stream data-input options)))))

(defn make-msgpack-encoder [options]
  (fn [data]
    (with-open [out-stream (ByteArrayOutputStream.)]
      (let [data-out (DataOutputStream. out-stream)]
        (msgpack/pack-stream (walk/stringify-keys data) data-out) options)
      (.toByteArray out-stream))))

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
    (fn [^String s]
      (when-not (.isEmpty (.trim s))
        (edn/read-string options s)))))

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
      (.toByteArray out))))

(defprotocol EncodeTransitJson
  (encode-transit-json [this]))

(defprotocol EncodeTransitMessagePack
  (encode-transit-msgpack [this]))
