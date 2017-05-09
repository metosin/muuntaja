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
  (:import (muuntaja.records FormatAndCharset Adapter Muuntaja)
           (java.nio.charset Charset)
           (java.io InputStream)))

;;
;; encode & decode
;;

(defn encoder [^Muuntaja m format]
  (if-let [^Adapter adapter ((.adapters m) format)]
    (.-encode adapter)))

(defn decoder [^Muuntaja m format]
  (if-let [^Adapter adapter ((.adapters m) format)]
    (.-decode adapter)))

(defn encode
  ([^Muuntaja m format data]
   (if-let [encode (encoder m format)]
     (encode data)
     (util/throw! m format "invalid encode format")))
  ([^Muuntaja m format data charset]
   (if-let [encode (encoder m format)]
     (encode data charset)
     (util/throw! m format "invalid encode format"))))

(defn decode
  ([^Muuntaja m format data]
   (if-let [decode (decoder m format)]
     (decode data)
     (util/throw! m format "invalid decode format")))
  ([^Muuntaja m format data charset]
   (if-let [decode (decoder m format)]
     (decode data charset)
     (util/throw! m format "invalid decode format"))))

;;
;; Creation
;;

(defn- key-set [m accept?]
  (set
    (for [[k v] m
          :when (accept? v)]
      k)))

(defn- matchers [formats]
  (->>
    (for [[name {:keys [matches]}] formats
          :when matches]
      [name matches])
    (into {})))

(defn- on-exception [^Exception e format type]
  (throw
    (ex-info
      (str "Malformed " format " in " type "")
      {:type type
       :format format}
      e)))

(defn- create-coder [format type spec spec-opts default-charset allow-empty-input-on-decode? [p pf]]
  (let [decode? (= type :muuntaja/decode)
        g (as-> spec $

                ;; duct-style generator
                (if (vector? $)
                  (let [[f opts] $]
                    (f (merge opts spec-opts)))
                  $)

                ;; optional guard on empty imput
                (if (and allow-empty-input-on-decode? decode?)
                  (fn [^InputStream is charset]
                    (if (and (not (nil? is)) (pos? (.available is))) ($ is charset)))
                  (fn [^InputStream is charset]
                    (if-not (nil? is) ($ is charset)))))
        prepare (if decode? protocols/as-input-stream identity)]
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

(defn- create-adapters [formats default-charset allow-empty-input-on-decode?]
  (->> (for [[format {:keys [decoder decoder-opts encoder encoder-opts encode-protocol]}] formats]
         (if-not (or encoder decoder)
           (throw
             (ex-info
               (str "invalid format: " format)
               {:format format
                :formats (keys formats)}))
           [format (records/map->Adapter
                     (merge
                       (if decoder {:decode (create-coder format :muuntaja/decode decoder decoder-opts default-charset allow-empty-input-on-decode? nil)})
                       (if encoder {:encode (create-coder format :muuntaja/encode encoder encoder-opts default-charset allow-empty-input-on-decode? encode-protocol)})))]))
       (into {})))

(declare default-options)
(declare http-create)

(defn- -create
  [{:keys [formats default-format default-charset allow-empty-input-on-decode?] :as options}]
  (let [adapters (create-adapters formats default-charset allow-empty-input-on-decode?)
        valid-format? (key-set formats identity)]
    (when-not (or (not default-format) (valid-format? default-format))
      (throw
        (ex-info
          (str "Invalid default format " default-format)
          {:formats valid-format?
           :default-format default-format})))
    (->
      (merge
        (dissoc options :formats)
        {:adapters adapters
         :consumes (key-set formats :decoder)
         :produces (key-set formats :encoder)
         :matchers (matchers formats)})
      (records/map->Muuntaja)
      (http-create))))

(defn create
  "Creates a new Muuntaja from a given prototype:
  - existing Muuntaja (no-op)
  - options-map (new created)
  - nothing (new created with default-options)"
  ([]
   (create default-options))
  ([prototype]
   (if (instance? Muuntaja prototype)
     prototype
     (-create prototype))))

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
  (get (:headers request) "content-type"))

(defn extract-accept-ring
  "Extracts accept from ring-request."
  [request]
  (get (:headers request) "accept"))

(defn extract-accept-charset-ring
  "Extracts accept-charset from ring-request."
  [request]
  (get (:headers request) "accept-charset"))

(defn encode-collections-with-override [_ response]
  (or
    (-> response :muuntaja/encode?)
    (-> response :body coll?)))

(def available-charsets
  "Set of recognised charsets by the current JVM"
  (into #{} (map str/lower-case (.keySet (Charset/availableCharsets)))))

(def default-options
  {:http {:extract-content-type extract-content-type-ring
          :extract-accept-charset extract-accept-charset-ring
          :extract-accept extract-accept-ring
          :decode-request-body? (constantly true)
          :encode-response-body? encode-collections-with-override}

   :allow-empty-input-on-decode? false

   :default-charset "utf-8"
   :charsets available-charsets

   :default-format "application/json"
   :formats {"application/json" json-format/json-format
             "application/edn" edn-format/edn-format
             "application/transit+json" transit-format/transit-json-format
             "application/transit+msgpack" transit-format/transit-msgpack-format
             #_#_"application/msgpack" msgpack-format/msgpack-format
             #_#_"application/x-yaml" yaml-format/yaml-format}})

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
       :default-format (:default-format m)
       :format (:format request-format-and-charset)
       :charset (:charset request-format-and-charset)
       :request request}
      e)))

(defn- fail-on-request-charset-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on request charset"
      {:type :muuntaja/request-charset-negotiation
       :charsets (:charsets formats)})))

(defn- fail-on-response-charset-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on response charset"
      {:type :muuntaja/response-charset-negotiation
       :charsets (:charsets formats)})))

(defn- fail-on-response-format-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on response format"
      {:type :muuntaja/response-format-negotiation
       :formats (:produces formats)})))

(defn- set-content-type [response content-type]
  (util/assoc-assoc response :headers "Content-Type" content-type))

(defn- content-type [format charset]
  (str format "; charset=" charset))

;;
;; Memoizable negotiation functions
;;

(defn- -negotiate-content-type [{:keys [consumes matchers default-charset charsets] :as formats} s]
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
          (fail-on-request-charset-negotiation formats))))))

;; TODO: fail if no match?
(defn- -negotiate-accept [{:keys [produces default-format]} s]
  (or
    (util/some-value
      produces
      (parse/parse-accept s))
    default-format))

;; TODO: fail if no match?
(defn- -negotiate-accept-charset [{:keys [default-charset charsets]} s]
  (or
    (util/some-value
      (or charsets identity)
      (parse/parse-accept-charset s))
    default-charset))

(defn http-create [^Muuntaja m]
  (-> m
      (merge (:http m))
      (dissoc :http)
      (assoc
        :negotiate-accept-charset
        (parse/fast-memoize
          (parse/cache 1000)
          (partial -negotiate-accept-charset m)))
      (assoc
        :negotiate-accept
        (parse/fast-memoize
          (parse/cache 1000)
          (partial -negotiate-accept m)))
      (assoc
        :negotiate-content-type
        (parse/fast-memoize
          (parse/cache 1000)
          (partial -negotiate-content-type m)))))

;;
;; Content Negotiation
;;

(defn- -negotiate-request [^Muuntaja m request]
  ((.negotiate_content_type m)
    ((.extract_content_type m)
      request)))

(defn- -negotiate-response [^Muuntaja m request]
  (records/->FormatAndCharset
    ((.negotiate_accept m)
      ((.extract_accept m)
        request))
    ((.negotiate_accept_charset m)
      ((.extract_accept_charset m)
        request))))

(defn- -decode-request? [^Muuntaja m request]
  (if-let [decode? (.decode_request_body_QMARK_ m)]
    (and (not (contains? request :muuntaja/format))
         (decode? request))))

(defn- -encode-response? [^Muuntaja m request response]
  (if-let [encode? (.encode_response_body_QMARK_ m)]
    (and (map? response)
         (not (contains? response :muuntaja/format))
         (encode? request response))))

;;
;; Request
;;

(defn- decode-request-body [^Muuntaja m request ^FormatAndCharset req-fc ^FormatAndCharset res-fc]
  (if (-decode-request? m request)
    (if-let [decode (decoder m (if req-fc (.-format req-fc)))]
      (try
        (decode (:body request) (.-charset req-fc))
        (catch Exception e
          (fail-on-request-decode-exception m e req-fc res-fc request))))))

(defn negotiate-request [^Muuntaja m request]
  (-> request
      (assoc :muuntaja/request (-negotiate-request m request))
      (assoc :muuntaja/response (-negotiate-response m request))))

(defn decode-request [^Muuntaja m request]
  (let [req-fc (:muuntaja/request request)
        res-fc (:muuntaja/response request)
        body (decode-request-body m request req-fc res-fc)]
    (cond-> request
            (not (nil? body)) (-> (assoc :muuntaja/format req-fc)
                                  (assoc :body-params body)))))

(defn format-request [^Muuntaja m request]
  (->> request
       (negotiate-request m)
       (decode-request m)))

;;
;; Response
;;

(defn- handle-response [response format encoder charset]
  (as-> response $
        (assoc $ :muuntaja/format format)
        (dissoc $ :muuntaja/content-type)
        (update $ :body encoder charset)
        (if-not (get (:headers $) "Content-Type")
          (set-content-type $ (content-type format charset))
          $)))

(defn- resolve-response-format [response ^Muuntaja m request]
  (or (if-let [ct (:muuntaja/content-type response)]
        ((.produces m) ct))
      (some-> request :muuntaja/response :format)
      (.default_format m)
      (fail-on-response-format-negotiation m)))

;; TODO: fail is negotiation fails!
(defn- resolve-response-charset [response ^Muuntaja m request]
  (or (if-let [ct (some-> request :muuntaja/response :charset)]
        ((.charsets m) ct))
      (.default_charset m)
      (fail-on-response-charset-negotiation m)))

(defn format-response [^Muuntaja m request response]
  (or
    (if (-encode-response? m request response)
      (if-let [format (resolve-response-format response m request)]
        (if-let [charset (resolve-response-charset response m request)]
          (if-let [encoder (encoder m format)]
            (handle-response response format encoder charset)))))
    response))

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
