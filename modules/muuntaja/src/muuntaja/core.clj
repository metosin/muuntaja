(ns muuntaja.core
  (:refer-clojure :exclude [slurp])
  (:require [clojure.string :as str]
            [muuntaja.parse :as parse]
            [muuntaja.util :as util]
            [muuntaja.protocols :as protocols]
            [muuntaja.format.core :as core]
            [muuntaja.format.json :as json-format]
            [muuntaja.format.edn :as edn-format]
            [muuntaja.format.transit :as transit-format]
            [clojure.set :as set])
  (:import (java.nio.charset Charset)
           (java.io EOFException IOException Writer ByteArrayInputStream)))

(defprotocol Muuntaja
  (encoder [this format])
  (decoder [this format])
  (adapters [this])
  (options [this]))

(defprotocol MuuntajaHttp
  (request-format [this request])
  (response-format [this request])

  (negotiate-request-response [this request])
  (format-request [this request])
  (format-response [this request response])
  (^:private -decode-response-body [this response])

  (negotiate-and-format-request [this request]))

(defn muuntaja? [x]
  (satisfies? Muuntaja x))

(defrecord FormatAndCharset [^String format, ^String charset, ^String raw-format])

(defmethod print-method FormatAndCharset
  [this ^Writer w]
  (.write w (str "#FormatAndCharset" (into {} this))))

(defrecord Adapter [encoder decoder])

;;
;; Documentation
;;

(defn decodes
  "Set of formats that the Muuntaja instance can decode"
  [m] (->> m (adapters) (filter #(-> % second :decoder)) (map first) (set)))

(defn encodes
  "Set of formats that the Muuntaja instance can encode"
  [m] (->> m (adapters) (filter #(-> % second :encoder)) (map first) (set)))

(defn matchers
  "Map of format->regexp that the Muuntaja instance knows"
  [m] (->> m (options) :formats (keep (fn [[n {:keys [matches]}]] (if matches [n matches]))) (into {})))

(defn charsets
  "Set of charsets that the Muuntaja instance can handle"
  [m] (->> m (options) :charsets))

(defn default-charset
  "Default charset of the Muuntaja instance"
  [m] (->> m (options) :default-charset))

(defn default-format
  "Default format of the Muuntaja instance"
  [m] (->> m (options) :default-format))

(defn formats
  "Set of formats that the Muuntaja instance can decode or encode"
  [m] (->> m (options) :formats (map first) (set)))

;;
;; default options
;;

(defn extract-content-type-ring
  "Extracts content-type from ring-request."
  [request]
  (or
    (:content-type request)
    (get (:headers request) "content-type")))

(defn extract-accept-ring
  "Extracts accept from ring-request."
  [request]
  (get (:headers request) "accept"))

(defn extract-accept-charset-ring
  "Extracts accept-charset from ring-request."
  [request]
  (get (:headers request) "accept-charset"))

(defn encode-collections [_ response]
  (-> response :body coll?))

(def available-charsets
  "Set of recognised charsets by the current JVM"
  (into #{} (map str/lower-case (.keySet (Charset/availableCharsets)))))

(def default-options
  {:http {:extract-content-type extract-content-type-ring
          :extract-accept-charset extract-accept-charset-ring
          :extract-accept extract-accept-ring
          :decode-request-body? (constantly true)
          :encode-response-body? encode-collections}

   :allow-empty-input? true
   :return :input-stream ;; :bytes :output-stream

   :default-charset "utf-8"
   :charsets available-charsets

   :default-format "application/json"
   :formats {"application/json" json-format/format
             "application/edn" edn-format/format
             "application/transit+json" transit-format/json-format
             "application/transit+msgpack" transit-format/msgpack-format}})

;;
;; HTTP stuff
;;

(defn- fail-on-request-decode-exception [m
                                         ^Exception e
                                         ^FormatAndCharset request-format-and-charset
                                         ^FormatAndCharset response-format-and-charset
                                         request]
  (throw
    (ex-info
      (str "Malformed " (:format request-format-and-charset) " request.")
      {:type :muuntaja/decode
       :default-format (default-format m)
       :format (:format request-format-and-charset)
       :charset (:charset request-format-and-charset)
       :request request}
      e)))

(defn- fail-on-response-decode-exception [m
                                          ^Exception e
                                          ^FormatAndCharset response-format-and-charset
                                          response]
  (throw
    (ex-info
      (str "Malformed " (:format response-format-and-charset) " response.")
      {:type :muuntaja/decode
       :default-format (default-format m)
       :format (:format response-format-and-charset)
       :charset (:charset response-format-and-charset)
       :response response}
      e)))

(defn- fail-on-response-decode [m response]
  (let [content-type (-> response :headers (get "Content-Type"))]
    (throw
      (ex-info
        (if content-type
          (str "Unknown response Content-Type: " content-type)
          "No Content-Type found")
        {:type :muuntaja/decode
         :default-format (default-format m)
         :response response}))))

(defn- fail-on-request-charset-negotiation [m]
  (throw
    (ex-info
      "Can't negotiate on request charset"
      {:type :muuntaja/request-charset-negotiation
       :charsets (charsets m)})))

(defn- fail-on-response-charset-negotiation [m]
  (throw
    (ex-info
      "Can't negotiate on response charset"
      {:type :muuntaja/response-charset-negotiation
       :charsets (charsets m)})))

(defn- fail-on-response-format-negotiation [m]
  (throw
    (ex-info
      "Can't negotiate on response format"
      {:type :muuntaja/response-format-negotiation
       :formats (encodes m)})))

(defn- set-content-type [response content-type]
  (util/assoc-assoc response :headers "Content-Type" content-type))

(defn- content-type [format charset]
  (str format "; charset=" charset))

;;
;; request helpers
;;

(defn get-request-format-and-charset [request]
  (:muuntaja/request request))

(defn get-response-format-and-charset [request]
  (:muuntaja/response request))

;;
;; response helpers
;;

(defn set-response-content-type [response content-type]
  (assoc response :muuntaja/content-type content-type))

;;
;; Memoizable negotiation functions
;;

(defn- -negotiate-content-type [m s]
  (let [consumes (decodes m)
        matchers (matchers m)
        default-charset (default-charset m)
        charsets (charsets m)]
    (if s
      (let [[content-type-raw charset-raw] (parse/parse-content-type s)]
        (->FormatAndCharset
          (if content-type-raw
            (or (consumes content-type-raw)
                (some
                  (fn [[name r]]
                    (if (and r (re-find r content-type-raw)) name))
                  matchers)))
          (or
            ;; if a provided charset was valid
            (and charset-raw charsets (charsets charset-raw) charset-raw)
            ;; only set default if none were set
            (and (not charset-raw) default-charset)
            ;; negotiation failed
            (fail-on-request-charset-negotiation m))
          content-type-raw)))))

(defn- -negotiate-accept [m parse s]
  (let [produces (encodes m)
        default-format (default-format m)
        accepts (parse s)]
    (or
      (and (not accepts) default-format)
      (util/some-value produces accepts)
      default-format
      (fail-on-response-format-negotiation m))))

(defn- -negotiate-accept-charset [m s]
  (let [charsets (charsets m)
        default-charset (default-charset m)
        accepts (parse/parse-accept-charset s)
        accept? (set accepts)]
    (or
      (and (not accepts) default-charset)
      (accept? default-charset)
      (util/some-value (or charsets identity) accepts)
      default-charset
      (fail-on-response-charset-negotiation m))))

;;
;; Creation
;;

(defn- key-set [m accept?]
  (set (for [[k v] m :when (accept? v)] k)))

;; TODO: we know th format, so we should delegate to formatter how to handle the empty input
(defn- on-exception [allow-empty-input? ^Exception e format type]
  (let [message (.getMessage e)]
    (if-not (try
              (and allow-empty-input?
                   (or
                     ;; msgpack
                     (instance? EOFException e)
                     ;; transit
                     (some->> e .getCause (instance? EOFException))
                     ;; jsonista
                     (and (instance? IOException e)
                          message (.startsWith message "No content to map due to end-of-input"))
                     ;; edn
                     (and (instance? RuntimeException e)
                          (= message "EOF while reading"))))
              (catch Exception _))
      (throw
        (ex-info
          (str "Malformed " format " in " type "")
          {:type type
           :format format}
          e)))))

(defn- create-coder [format key type spec opts spec-opts default-charset allow-empty-input? return]
  (let [decode? (= type :muuntaja/decode)
        coder (if (vector? spec)
                (let [[f args] spec]
                  (f (merge args opts spec-opts)))
                spec)
        in (if decode? protocols/into-input-stream identity)
        on-exception (partial on-exception allow-empty-input?)
        ensure! (fn [protocol f]
                  (if (satisfies? protocol coder)
                    f
                    (throw
                      (ex-info
                        (str "Invalid format " (pr-str format) " for type " type ". "
                             "It should satisfy " (pr-str (:on protocol)))
                        {:format format
                         :type type
                         :spec spec
                         :coder coder
                         :protocol protocol}))))]

    {key
     (if decode?
       (ensure!
         core/Decode
         (fn decode
           ([data]
            (decode data default-charset))
           ([data charset]
            (try
              (core/decode coder (in data) charset)
              (catch Exception e
                (on-exception e format type))))))
       (case return
         :bytes
         (ensure!
           core/EncodeToBytes
           (fn encode
             ([data]
              (encode data default-charset))
             ([data charset]
              (try
                (core/encode-to-bytes coder data charset)
                (catch Exception e
                  (on-exception e format type))))))
         :input-stream
         (ensure!
           core/EncodeToBytes
           (fn encode
             ([data]
              (encode data default-charset))
             ([data charset]
              (try
                (ByteArrayInputStream.
                  (core/encode-to-bytes coder data charset))
                (catch Exception e
                  (on-exception e format type))))))
         :output-stream
         (ensure!
           core/EncodeToOutputStream
           (fn encode
             ([data]
              (encode data default-charset))
             ([data charset]
              (protocols/->StreamableResponse
                (core/encode-to-output-stream coder data charset)))))))}))

(defn- create-adapters [formats default-charset allow-empty-input? default-return]
  (->> (for [[format {:keys [opts decoder decoder-opts encoder encoder-opts return]}] formats]
         (let [return (or return default-return)]
           (if-not (or encoder decoder)
             (throw
               (ex-info
                 (str "invalid format: " format)
                 {:format format
                  :formats (keys formats)}))
             [format (map->Adapter
                       (merge
                         (if decoder (create-coder format :decoder :muuntaja/decode decoder opts decoder-opts default-charset allow-empty-input? return))
                         (if encoder (create-coder format :encoder :muuntaja/encode encoder opts encoder-opts default-charset allow-empty-input? return))))])))
       (into {})))

(defn create
  "Creates a new Muuntaja intance from a given prototype:
  - existing Muuntaja (no-op)
  - options-map (new created)
  - nothing (new created with default-options)"
  ([]
   (create default-options))
  ([muuntaja-or-options]
   (if (satisfies? Muuntaja muuntaja-or-options)
     muuntaja-or-options
     (let [{:keys [formats
                   default-format
                   charsets
                   default-charset
                   return
                   allow-empty-input?] :as options} muuntaja-or-options
           adapters (create-adapters formats default-charset allow-empty-input? return)
           valid-format? (key-set formats identity)]
       (when-not (or (not default-format) (valid-format? default-format))
         (throw
           (ex-info
             (str "Invalid default format " default-format)
             {:formats valid-format?
              :default-format default-format})))
       (let [m (reify Muuntaja
                 (adapters [_] adapters)
                 (options [_] options))
             {:keys [extract-content-type
                     extract-accept-charset
                     extract-accept
                     decode-request-body?
                     encode-response-body?]} (:http options)
             produces (encodes m)
             -encoder (fn [format]
                        (if-let [^Adapter adapter (adapters format)]
                          (.-encoder adapter)))
             -decoder (fn [format]
                        (if-let [^Adapter adapter (adapters format)]
                          (.-decoder adapter)))
             -negotiate-accept-charset (parse/fast-memoize 1000 (partial -negotiate-accept-charset m))
             -parse-accept (parse/fast-memoize 1000 parse/parse-accept)
             -negotiate-accept (parse/fast-memoize 1000 (partial -negotiate-accept m -parse-accept))
             -negotiate-content-type (parse/fast-memoize 1000 (partial -negotiate-content-type m))
             -decode-request-body? (fn [request]
                                     (and (not (:body-params request))
                                          (decode-request-body? request)))
             -decode-request-body (fn [m request ^FormatAndCharset req-fc ^FormatAndCharset res-fc]
                                    (if (-decode-request-body? request)
                                      (if-let [decode (decoder m (if req-fc (.-format req-fc)))]
                                        (try
                                          (decode (:body request) (.-charset req-fc))
                                          (catch Exception e
                                            (fail-on-request-decode-exception m e req-fc res-fc request))))))
             -encode-response? (fn [request response]
                                 (and (or (not (contains? (:headers response) "Content-Type"))
                                          (:muuntaja/encode response))
                                      (encode-response-body? request response)))
             -resolve-response-charset (fn [_ request]
                                         (or (if-let [ct (some-> request :muuntaja/response :charset)]
                                               (charsets ct))
                                             default-charset
                                             (fail-on-response-charset-negotiation m)))
             -resolve-response-format (fn [response request]
                                        (or (if-let [ct (:muuntaja/content-type response)]
                                              (produces ct))
                                            (some-> request :muuntaja/response :format)
                                            default-format
                                            (fail-on-response-format-negotiation m)))
             -handle-response (fn [response format encoder charset]
                                (as-> response $
                                      (dissoc $ :muuntaja/content-type)
                                      (update $ :body encoder charset)
                                      (if-not (get (:headers $) "Content-Type")
                                        (set-content-type $ (content-type format charset))
                                        $)))]
         ^{:type ::muuntaja}
         (reify
           Muuntaja
           (encoder [_ format]
             (-encoder format))
           (decoder [_ format]
             (-decoder format))
           (adapters [_]
             adapters)
           (options [_]
             options)

           MuuntajaHttp
           (request-format [_ request]
             (-negotiate-content-type (extract-content-type request)))
           (response-format [_ request]
             (let [accept-raw (extract-accept request)
                   charset-raw (extract-accept-charset request)]
               (->FormatAndCharset
                 (-negotiate-accept accept-raw)
                 (-negotiate-accept-charset charset-raw)
                 (first (-parse-accept accept-raw)))))
           (negotiate-request-response [this request]
             (-> request
                 (assoc :muuntaja/request (request-format this request))
                 (assoc :muuntaja/response (response-format this request))))
           (format-request [this request]
             (let [req-fc (:muuntaja/request request)
                   res-fc (:muuntaja/response request)
                   body (-decode-request-body this request req-fc res-fc)]
               (if (not (nil? body))
                 (assoc request :body-params body)
                 request)))
           (format-response [_ request response]
             (or
               (if (-encode-response? request response)
                 (if-let [format (-resolve-response-format response request)]
                   (if-let [charset (-resolve-response-charset response request)]
                     (if-let [encoder (-encoder format)]
                       (-handle-response response format encoder charset)))))
               response))
           (negotiate-and-format-request [this request]
             (let [req-fc (request-format this request)
                   res-fc (response-format this request)
                   body (-decode-request-body this request req-fc res-fc)]
               (as-> request $
                     (assoc $ :muuntaja/request req-fc)
                     (assoc $ :muuntaja/response res-fc)
                     (if (not (nil? body))
                       (assoc $ :body-params body)
                       $))))
           (-decode-response-body [this response]
             (or
               (if-let [res-fc (-> response :headers (get "Content-Type") -negotiate-content-type)]
                 (if-let [decode (decoder this (:format res-fc))]
                   (try
                     (decode (:body response) (:charset res-fc))
                     (catch Exception e
                       (fail-on-response-decode-exception m e res-fc response)))))
               (fail-on-response-decode m response)))))))))

(def instance "the default instance" (create))

(defmethod print-method ::muuntaja
  [_ ^Writer w]
  (.write w (str "<<Muuntaja>>")))

;;
;; encode & decode
;;

(defn encode
  "Encode data into the given format. Returns InputStream or throws."
  ([format data]
   (encode instance format data))
  ([m format data]
   (encode m format data (default-charset m)))
  ([m format data charset]
   (if-let [encoder (encoder m format)]
     (encoder data charset)
     (util/throw! m format "encoder not found for"))))

(defn decode
  "Decode data into the given format. Returns Clojure Data or throws."
  ([format data]
   (decode instance format data))
  ([m format data]
   (decode m format data (default-charset m)))
  ([m format data charset]
   (if-let [decoder (decoder m format)]
     (decoder data charset)
     (util/throw! m format "decoder not found for"))))

(defn decode-response-body
  "Decode response :body using the format defined by \"Content-Type\" header.
  Returns Clojure Data or throws."
  ([response]
   (-decode-response-body instance response))
  ([m response]
   (-decode-response-body m response)))

;;
;; options
;;

(defn transform-formats [options f]
  (update options :formats #(into (empty %) (map (fn [[k v]] [k (f k v)]) %))))

(defn select-formats [options formats]
  (let [existing-formats (-> options :formats keys set)
        future-formats (set formats)]
    (when-let [diff (seq (set/difference future-formats existing-formats))]
      (throw
        (ex-info
          (str "invalid formats: " diff)
          {:invalid (seq diff)
           :formats (seq formats)
           :existing (seq existing-formats)})))
    (-> options
        (update :formats select-keys formats)
        (assoc :default-format (first formats)))))

(defn install
  ([options format]
   (install options format (:name format)))
  ([options format name]
   (assert name (str "no name in " format))
   (assoc-in options [:formats name] (core/map->Format format))))

;;
;; Utilities
;;

(defn slurp [x]
  (some-> x protocols/into-input-stream clojure.core/slurp))
