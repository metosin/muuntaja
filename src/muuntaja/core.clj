(ns muuntaja.core
  (:require [muuntaja.parse :as parse]
            [muuntaja.formats :as formats]
            [muuntaja.protocols :as protocols]
            [clojure.set :as set]
            [clojure.string :as str])
  (:import (java.nio.charset Charset)
           (java.io Writer)))

(set! *warn-on-reflection* true)

(defrecord FormatAndCharset [^String format, ^String charset])

(defn- throw! [formats format message]
  (throw
    (ex-info
      (str message ": " format)
      {:formats (-> formats :formats keys)
       :format format})))

(defn- some-value [pred c]
  (let [f (fn [x] (if (pred x) x))]
    (some f c)))

(defn- assoc-assoc [m k1 k2 v]
  (assoc m k1 (assoc (k1 m) k2 v)))

(defn- fail-on-request-decode-exception [m
                                         ^Exception e
                                         ^FormatAndCharset request-format-and-charset
                                         ^FormatAndCharset response-format-and-charset
                                         request]
  (throw
    (ex-info
      (str "Malformed " (:format request-format-and-charset) " request.")
      {:type ::decode
       :default-format (:default-format m)
       :format (:format request-format-and-charset)
       :charset (:charset request-format-and-charset)
       :request request}
      e)))

(defn- fail-on-request-charset-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on request charset"
      {:type ::request-charset-negotiation
       :charsets (:charsets formats)})))

(defn- fail-on-response-charset-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on response charset"
      {:type ::response-charset-negotiation
       :charsets (:charsets formats)})))

(defn- fail-on-response-format-negotiation [formats]
  (throw
    (ex-info
      "Can't negotiate on response format"
      {:type ::response-format-negotiation
       :formats (:produces formats)})))

(defn- set-content-type [response content-type]
  (assoc-assoc response :headers "Content-Type" content-type))

(defn- content-type [format charset]
  (str format "; charset=" charset))

;;
;; Memoizable negotiation functions
;;

(defn- -negotiate-content-type [{:keys [consumes matchers default-charset charsets] :as formats} s]
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
          (fail-on-request-charset-negotiation formats))))))

;; TODO: fail if no match?
(defn- -negotiate-accept [{:keys [produces default-format]} s]
  (or
    (some-value
      produces
      (parse/parse-accept s))
    default-format))

;; TODO: fail if no match?
(defn- -negotiate-accept-charset [{:keys [default-charset charsets]} s]
  (or
    (some-value
      (or charsets identity)
      (parse/parse-accept-charset s))
    default-charset))

;;
;; Records
;;

(defrecord Adapter [encode decode])

(defrecord Muuntaja [negotiate-content-type
                     negotiate-accept
                     negotiate-accept-charset

                     extract-content-type
                     extract-accept
                     extract-accept-charset

                     encode-response-body?
                     decode-request-body?

                     produces
                     consumes
                     matchers

                     adapters
                     charsets
                     default-charset
                     default-format])

(defmethod print-method Muuntaja
  [this ^Writer w]
  (.write w (str "#Muuntaja" (select-keys this [:produces :consumes :default-charset :default-format]))))

;;
;; Content Negotiation
;;

(defn negotiate-request [^Muuntaja m request]
  ((.negotiate_content_type m)
    ((.extract_content_type m)
      request)))

(defn negotiate-response [^Muuntaja m request]
  (->FormatAndCharset
    ((.negotiate_accept m)
      ((.extract_accept m)
        request))
    ((.negotiate_accept_charset m)
      ((.extract_accept_charset m)
        request))))

(defn decode-request? [^Muuntaja m request]
  (if-let [decode? (.decode_request_body_QMARK_ m)]
    (and (not (contains? request ::format))
         (decode? request))))

(defn encode-response? [^Muuntaja m request response]
  (if-let [encode? (.encode_response_body_QMARK_ m)]
    (and (map? response)
         (not (contains? response ::format))
         (encode? request response))))

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
     (throw! m format "invalid encode format")))
  ([^Muuntaja m format data charset]
   (if-let [encode (encoder m format)]
     (encode data charset)
     (throw! m format "invalid encode format"))))

(defn decode
  ([^Muuntaja m format data]
   (if-let [decode (decoder m format)]
     (decode data)
     (throw! m format "invalid decode format")))
  ([^Muuntaja m format data charset]
   (if-let [decode (decoder m format)]
     (decode data charset)
     (throw! m format "invalid decode format"))))

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

(defn- create-coder [format type spec spec-opts default-charset [p pf]]
  (let [g (if (vector? spec)
            (let [[f opts] spec]
              (f (merge opts spec-opts)))
            spec)
        prepare (if (= type ::decode) protocols/as-input-stream identity)]
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

(defn- create-adapters [formats default-charset]
  (->> (for [[format {:keys [decoder decoder-opts encoder encoder-opts encode-protocol]}] formats]
         (if-not (or encoder decoder)
           (throw
             (ex-info
               (str "invalid format: " format)
               {:format format
                :formats (keys formats)}))
           [format (map->Adapter
                     (merge
                       (if decoder {:decode (create-coder format ::decode decoder decoder-opts default-charset nil)})
                       (if encoder {:encode (create-coder format ::encode encoder encoder-opts default-charset encode-protocol)})))]))
       (into {})))

(defn- http-options [options]
  (when options
    (assert (:extract-content-type options))
    (assert (:extract-accept-charset options))
    (assert (:extract-accept options))
    (assert (:decode-request-body? options))
    (assert (:encode-response-body? options))
    options))

(declare default-options)

(defn- -create
  [{:keys [formats default-format default-charset http] :as options}]
  (let [adapters (create-adapters formats default-charset)
        valid-format? (key-set formats identity)
        m (map->Muuntaja
            (merge
              (dissoc options :formats :http)
              ;; TODO: don't copy here.
              (http-options http)
              {:adapters adapters
               :consumes (key-set formats :decoder)
               :produces (key-set formats :encoder)
               :matchers (matchers formats)}))]
    (when-not (or (not default-format) (valid-format? default-format))
      (throw
        (ex-info
          (str "Invalid default format " default-format)
          {:formats valid-format?
           :default-format default-format})))
    (-> m
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
            (partial -negotiate-content-type m))))))

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
;; Request
;;

(defn- decode-request [^Muuntaja m request ^FormatAndCharset req-fc ^FormatAndCharset res-fc]
  (if (decode-request? m request)
    (if-let [decode (decoder m (if req-fc (.-format req-fc)))]
      (try
        (decode (:body request) (.-charset req-fc))
        (catch Exception e
          (fail-on-request-decode-exception m e req-fc res-fc request))))))

(defn negotiate-ring-request [^Muuntaja m request]
  (-> request
      (assoc ::request (negotiate-request m request))
      (assoc ::response (negotiate-response m request))))

(defn decode-ring-request [^Muuntaja m request]
  (let [req-fc (::request request)
        res-fc (::response request)
        body (decode-request m request req-fc res-fc)]
    (cond-> request
            body (-> (assoc ::format req-fc)
                     (assoc :body-params body)))))

(defn format-request [^Muuntaja m request]
  (->> request
       (negotiate-ring-request m)
       (decode-ring-request m)))

;;
;; Response
;;

(defn- handle-response [response format encoder charset]
  (as-> response $
        (assoc $ ::format format)
        (dissoc $ ::content-type)
        (update $ :body encoder charset)
        (if-not (get (:headers $) "Content-Type")
          (set-content-type $ (content-type format charset))
          $)))

(defn- resolve-response-format [response ^Muuntaja m request]
  (or (if-let [ct (::content-type response)]
        ((.produces m) ct))
      (some-> request ::response :format)
      (.default_format m)
      (fail-on-response-format-negotiation m)))

;; TODO: fail is negotiation fails!
(defn- resolve-response-charset [response ^Muuntaja m request]
  (or (if-let [ct (some-> request ::response :charset)]
        ((.charsets m) ct))
      (.default_charset m)
      (fail-on-response-charset-negotiation m)))

(defn format-response [^Muuntaja m request response]
  (or
    (if (encode-response? m request response)
      (if-let [format (resolve-response-format response m request)]
        (if-let [charset (resolve-response-charset response m request)]
          (if-let [encoder (encoder m format)]
            (handle-response response format encoder charset)))))
    response))

;;
;; Options
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
    (-> response ::encode?)
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

   :default-charset "utf-8"
   :charsets available-charsets

   :default-format "application/json"
   :formats {"application/json" {:decoder [formats/make-json-decoder {:key-fn true}]
                                 :encoder [formats/make-json-encoder]
                                 :encode-protocol [formats/EncodeJson formats/encode-json]}
             "application/edn" {:decoder [formats/make-edn-decoder]
                                :encoder [formats/make-edn-encoder]
                                :encode-protocol [formats/EncodeEdn formats/encode-edn]}
             "application/msgpack" {:decoder [formats/make-msgpack-decoder {:keywords? true}]
                                    :encoder [formats/make-msgpack-encoder]
                                    :encode-protocol [formats/EncodeMsgpack formats/encode-msgpack]}
             "application/x-yaml" {:decoder [formats/make-yaml-decoder {:keywords true}]
                                   :encoder [formats/make-yaml-encoder]
                                   :encode-protocol [formats/EncodeYaml formats/encode-yaml]}
             "application/transit+json" {:decoder [(partial formats/make-transit-decoder :json)]
                                         :encoder [(partial formats/make-transit-encoder :json)]
                                         :encode-protocol [formats/EncodeTransitJson formats/encode-transit-json]}
             "application/transit+msgpack" {:decoder [(partial formats/make-transit-decoder :msgpack)]
                                            :encoder [(partial formats/make-transit-encoder :msgpack)]
                                            :encode-protocol [formats/EncodeTransitMessagePack formats/encode-transit-msgpack]}}})

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
;; request helpers
;;

(defn disable-request-decoding [request]
  (assoc request ::format nil))

;;
;; response helpers
;;

(defn disable-response-encoding [response]
  (assoc response ::format nil))

(defn set-response-content-type [response content-type]
  (assoc response ::content-type content-type))
