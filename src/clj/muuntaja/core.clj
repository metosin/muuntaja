(ns muuntaja.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [muuntaja.parse :as parse]
            [muuntaja.util :as util]
            [muuntaja.protocols :as protocols]
            [muuntaja.records :as records]
            [muuntaja.format.json :as json-format]
            [muuntaja.format.edn :as edn-format]
            [muuntaja.format.transit :as transit-format])
  (:import (muuntaja.records FormatAndCharset Adapter)
           (java.nio.charset Charset)
           (java.io EOFException IOException Writer)))

;;
;; Protocol
;;

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

  (negotiate-and-format-request [this request]))

(defn muuntaja? [x]
  (satisfies? Muuntaja x))

;;
;; encode & decode
;;

(defn encode
  "Encode data into the given format. Returns InputStream or throws."
  ([m format data]
   (if-let [encode (encoder m format)]
     (encode data)
     (util/throw! m format "invalid encode format")))
  ([m format data charset]
   (if-let [encode (encoder m format)]
     (encode data charset)
     (util/throw! m format "invalid encode format"))))

(defn decode
  "Decode data into the given format. Returns InputStream or throws."
  ([m format data]
   (if-let [decode (decoder m format)]
     (decode data)
     (util/throw! m format "invalid decode format")))
  ([m format data charset]
   (if-let [decode (decoder m format)]
     (decode data charset)
     (util/throw! m format "invalid decode format"))))

;;
;; Documentation
;;

(defn decodes
  "Set of formats that the Muuntaja instance can decode"
  [m] (->> m (adapters) (filter #(-> % second :decode)) (map first) (set)))

(defn encodes
  "Set of formats that the Muuntaja instance can encode"
  [m] (->> m (adapters) (filter #(-> % second :encode)) (map first) (set)))

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
;; transforming options
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

(defn encode-collections-with-override [_ response]
  (or (-> response :body coll?)
      (-> response :muuntaja/encode?)))

(def available-charsets
  "Set of recognised charsets by the current JVM"
  (into #{} (map str/lower-case (.keySet (Charset/availableCharsets)))))

(def default-options
  {:http {:extract-content-type extract-content-type-ring
          :extract-accept-charset extract-accept-charset-ring
          :extract-accept extract-accept-ring
          :decode-request-body? (constantly true)
          :encode-response-body? encode-collections-with-override}

   :allow-empty-input? true

   :default-charset "utf-8"
   :charsets available-charsets

   :default-format "application/json"
   :formats {"application/json" json-format/json-format
             "application/edn" edn-format/edn-format
             "application/transit+json" transit-format/transit-json-format
             "application/transit+msgpack" transit-format/transit-msgpack-format}})

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

(defn disable-request-decoding [request]
  (assoc request :muuntaja/format nil))

;;
;; response helpers
;;

(defn disable-response-encoding [response]
  (assoc response :muuntaja/format nil))

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
        (records/->FormatAndCharset
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
            (fail-on-request-charset-negotiation m)))))))

;; TODO: fail if no match?
(defn- -negotiate-accept [m s]
  (let [produces (encodes m)
        default-format (default-format m)]
    (or
      (util/some-value
        produces
        (parse/parse-accept s))
      default-format)))

;; TODO: fail if no match?
(defn- -negotiate-accept-charset [m s]
  (let [charsets (charsets m)
        default-charset (default-charset m)]
    (or
      (util/some-value
        (or charsets identity)
        (parse/parse-accept-charset s))
      default-charset)))

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


(defn- create-coder [format type spec spec-opts default-charset allow-empty-input? [p pf]]
  (let [decode? (= type :muuntaja/decode)
        g (if (vector? spec)
            (let [[f opts] spec]
              (f (merge opts spec-opts)))
            spec)
        prepare (if decode? protocols/as-input-stream identity)
        on-exception (partial on-exception allow-empty-input?)]
    (if (and p pf)
      (fn f
        ([x]
         (f x default-charset))
        ([x charset]
         (try
           (if (and (record? x) (satisfies? p x))
             (pf x charset)
             (g (prepare x) charset))
           (catch Exception e
             (on-exception e format type)))))
      (fn f
        ([x]
         (f x default-charset))
        ([x charset]
         (try
           (g (prepare x) charset)
           (catch Exception e
             (on-exception e format type))))))))

(defn- create-adapters [formats default-charset allow-empty-input?]
  (->> (for [[format {:keys [decoder decoder-opts encoder encoder-opts encode-protocol]}] formats]
         (if-not (or encoder decoder)
           (throw
             (ex-info
               (str "invalid format: " format)
               {:format format
                :formats (keys formats)}))
           [format (records/map->Adapter
                     (merge
                       (if decoder {:decode (create-coder format :muuntaja/decode decoder decoder-opts default-charset allow-empty-input? nil)})
                       (if encoder {:encode (create-coder format :muuntaja/encode encoder encoder-opts default-charset allow-empty-input? encode-protocol)})))]))
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
                   allow-empty-input?] :as options} muuntaja-or-options
           adapters (create-adapters formats default-charset allow-empty-input?)
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
                          (.-encode adapter)))
             -decoder (fn [format]
                        (if-let [^Adapter adapter (adapters format)]
                          (.-decode adapter)))
             -negotiate-accept-charset (parse/fast-memoize 1000 (partial -negotiate-accept-charset m))
             -negotiate-accept (parse/fast-memoize 1000 (partial -negotiate-accept m))
             -negotiate-content-type (parse/fast-memoize 1000 (partial -negotiate-content-type m))
             -decode-request? (fn [request]
                                (and (not (contains? request :muuntaja/format))
                                     (decode-request-body? request)))
             -decode-request-body (fn [m request ^FormatAndCharset req-fc ^FormatAndCharset res-fc]
                                    (if (-decode-request? request)
                                      (if-let [decode (decoder m (if req-fc (.-format req-fc)))]
                                        (try
                                          (decode (:body request) (.-charset req-fc))
                                          (catch Exception e
                                            (fail-on-request-decode-exception m e req-fc res-fc request))))))
             -encode-response? (fn [request response]
                                 (and (map? response)
                                      (not (contains? response :muuntaja/format))
                                      (encode-response-body? request response)))
             -resolve-response-charset (fn [response request]
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
                                      (assoc $ :muuntaja/format format)
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
             (records/->FormatAndCharset
               (-negotiate-accept (extract-accept request))
               (-negotiate-accept-charset (extract-accept-charset request))))
           (negotiate-request-response [this request]
             (-> request
                 (assoc :muuntaja/request (request-format this request))
                 (assoc :muuntaja/response (response-format this request))))
           (format-request [this request]
             (let [req-fc (:muuntaja/request request)
                   res-fc (:muuntaja/response request)
                   body (-decode-request-body this request req-fc res-fc)]
               (cond-> request
                       (not (nil? body)) (-> (assoc :muuntaja/format req-fc)
                                             (assoc :body-params body)))))
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
                       (-> $
                           (assoc :muuntaja/format req-fc)
                           (assoc :body-params body))
                       $))))))))))

(defmethod print-method ::muuntaja
  [_ ^Writer w]
  (.write w (str "<<Muuntaja>>")))
