(ns ring-format.adapters
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.tools.reader.edn :as edn]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack]
            [clojure.java.io :as io])
  (:import [java.io InputStream ByteArrayOutputStream DataInputStream DataOutputStream]))

(set! *warn-on-reflection* true)

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

(defn make-json-decoder [{:keys [key-fn array-coerce-fn]}]
  (fn [s] (json/parse-string s key-fn array-coerce-fn)))

(defn make-json-encoder [options]
  (fn [data] (json/generate-string data options)))

;; msgpack

(defn make-msgpack-decoder [options]
  (fn [in]
    (with-open [i (io/input-stream (slurp-to-bytes in))]
      (let [data-input (DataInputStream. i)]
        (msgpack/unpack-stream data-input options)))))

(defn make-msgpack-encoder [options]
  (fn [data]
    (with-open [out-stream (ByteArrayOutputStream.)]
      (let [data-out (DataOutputStream. out-stream)]
        (msgpack/pack-stream (walk/stringify-keys data) data-out) options)
      (.toByteArray out-stream))))

;; YAML

(defn make-yaml-decoder [options]
  (let [options-args (mapcat identity options)]
    (fn [s] (apply yaml/parse-string s options-args))))

(defn make-yaml-encoder [options]
  (let [options-args (mapcat identity options)]
    (fn [data]
      (apply yaml/generate-string data options-args))))

;; EDN

(defn make-edn-decoder [options]
  (let [options (merge {:readers *data-readers*} options)]
    (fn [^String s]
      (when-not (.isEmpty (.trim s))
        (edn/read-string options s)))))

(defn encode-edn [data]
  (pr-str data))

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

;;
;; Adapters
;;

(defn make-adapters [adapters formats]
  (let [make (fn [spec-opts spec]
               (if (vector? spec)
                 (let [[f opts] spec]
                   (f (merge opts spec-opts)))
                 spec))]
    (->> formats
         (keep identity)
         (mapv (fn [format]
                 (if-let [{:keys [decoder decoder-opts encoder encoder-opts] :as adapter}
                          (if (map? format) format (get adapters format))]
                   (cond-> adapter
                           decoder (update :decoder (partial make decoder-opts))
                           encoder (update :encoder (partial make encoder-opts))))))
         (keep identity))))

(def default-adapters
  {:json {:format :json
          :decoder [make-json-decoder]
          :encoder [make-json-encoder]}
   :edn {:format :edn
         :decoder [make-edn-decoder]
         :encoder encode-edn}
   :msgpack {:format :msgpack
             :decoder [make-msgpack-decoder]
             :encoder [make-msgpack-encoder]
             :binary? true}
   :yaml {:format :yaml
          :decoder [make-yaml-decoder {:keywords false}]
          :encoder [make-yaml-encoder]}
   :transit-json {:format :transit-json
                  :decoder [(partial make-transit-decoder :json)]
                  :encoder [(partial make-transit-encoder :json)]
                  :binary? true}
   :transit-msgpack {:format :transit-msgpack
                     :decoder [(partial make-transit-decoder :msgpack)]
                     :encoder [(partial make-transit-encoder :msgpack)]
                     :binary? true}})
