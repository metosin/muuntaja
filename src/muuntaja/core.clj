(ns muuntaja.core
  (:require [clojure.string :as str]
            [muuntaja.parse :as parse]
            [muuntaja.formats :as formats]))

(defn- some-value [pred c]
  (let [f (fn [x] (if (pred x) x))]
    (some f c)))

(defn- assoc-assoc [m k1 k2 v]
  (assoc m k1 (assoc (k1 m) k2 v)))

(defn- on-decode-exception [^Exception e format request]
  (throw
    (ex-info
      (str "Malformed " format " request.")
      {:type ::decode
       :format format
       :request request}
      e)))

(defn- set-content-type [response content-type]
  (assoc-assoc response :headers "Content-Type" content-type))

(defn- content-type [formats format]
  (str ((:produces formats) format) "; charset=" (:charset formats)))

;;
;; Protocols
;;

(defprotocol RequestFormatter
  (negotiate-request [_ request])
  (negotiate-response [_ request])
  (decode-request? [_ request])
  (encode-response? [_ request response]))

(defprotocol Formatter
  (encoder [_ format])
  (decoder [_ format])
  (default-format [_]))

;;
;; Content negotiation
;;

(defn- -negotiate-content-type [{:keys [consumes matchers charset]} ^String s]
  (if s
    (let [[content-type-raw charset-raw] (parse/parse-content-type s)]
      [(if content-type-raw
         (or (get consumes content-type-raw)
             (loop [i 0]
               (let [[f r] (nth matchers i)]
                 (cond
                   (re-find r content-type-raw) f
                   (< (inc i) (count matchers)) (recur (inc i)))))))
       (or charset-raw charset)])))

(defn- -negotiate-accept [{:keys [consumes produces] :as formats} ^String s]
  (consumes
    (or
      (some-value
        consumes
        (parse/parse-accept s))
      (produces
        (default-format formats)))))

(defn- -negotiate-accept-charset [formats s]
  (or
    (some-value
      (or (:charsets formats) identity)
      (parse/parse-accept-charset s))
    (:charset formats)))

;;
;; Records
;;

(defrecord Adapter [encode decode])

(defrecord Formats [negotiate-content-type
                    negotiate-accept
                    negotiate-accept-charset

                    extract-content-type-fn
                    extract-accept-fn
                    extract-accept-charset-fn

                    encode?
                    decode?

                    encode-error-fn

                    consumes
                    matchers

                    adapters
                    formats
                    default-format]
  RequestFormatter

  (negotiate-request [_ request]
    (negotiate-content-type (extract-content-type-fn request)))

  (negotiate-response [_ request]
    [(negotiate-accept (extract-accept-fn request))
     (negotiate-accept-charset (extract-accept-charset-fn request))])

  (decode-request? [_ request]
    (and decode?
         (not (contains? request ::adapter))
         (decode? request)))

  (encode-response? [_ request response]
    (and encode?
         (map? response)
         (not (contains? response ::adapter))
         (encode? request response)))

  Formatter
  (encoder [_ format]
    (-> format adapters :encode))

  (decoder [_ format]
    (-> format adapters :decode))

  (default-format [_]
    default-format))

(defn encode [formats format data]
  (if-let [encode (encoder formats format)]
    (encode data)))

(defn decode [formats format data]
  (if-let [decode (decoder formats format)]
    (decode data)))

;;
;; Content-type resolution
;;

(defn- content-type->format [format-types]
  (reduce
    (fn [acc [k type]]
      (let [old-k (acc type)]
        (when (and old-k (not= old-k k))
          (throw (ex-info "content-type refers to multiple formats" {:content-type type
                                                                     :formats [k old-k]}))))
      (assoc acc type k))
    {}
    (for [[k type-or-types] format-types
          :let [types (flatten (vector type-or-types))]
          type types
          :when (string? type)]
      [k type])))

(defn- format-regexps [format-types]
  (reduce
    (fn [acc [k type]]
      (conj acc [k type]))
    []
    (for [[k type-or-types] format-types
          :let [types (flatten (vector type-or-types))]
          type types
          :when (not (string? type))]
      [k type])))

(defn- format->content-type [format-types]
  (reduce
    (fn [acc [k type]]
      (if-not (acc k)
        (assoc acc k type)
        acc))
    {}
    (for [[k type-or-types] format-types
          :let [types (flatten (vector type-or-types))]
          type types
          :when (string? type)]
      [k type])))

(defn- compile-adapters [adapters formats]
  (let [make (fn [spec spec-opts [p pf]]
               (let [g (if (vector? spec)
                         (let [[f opts] spec]
                           (f (merge opts spec-opts)))
                         spec)]
                 (if (and p pf)
                   (fn [x]
                     (if (and (record? x) (satisfies? p x))
                       (pf x)
                       (g x)))
                   g)))]
    (->> formats
         (keep identity)
         (mapv (fn [format]
                 (if-let [{:keys [decoder decoder-opts encoder encoder-opts encode-protocol] :as adapter}
                          (if (map? format) format (get adapters format))]
                   [format (map->Adapter
                             (merge
                               (if decoder {:decode (make decoder decoder-opts nil)})
                               (if encoder {:encode (make encoder encoder-opts encode-protocol)})))]
                   (throw (ex-info (str "no adapter for: " format) {:supported (keys adapters)
                                                                    :format format})))))
         (into {}))))

(defn create [{:keys [adapters formats] :as options}]
  (let [selected-format? (set formats)
        format-types (for [[k {:keys [format]}] adapters
                           :when (selected-format? k)]
                       [k format])
        adapters (compile-adapters adapters formats)
        m (map->Formats
            (merge
              options
              {:default-format (first formats)
               :adapters adapters
               :consumes (content-type->format format-types)
               :produces (format->content-type format-types)
               :matchers (format-regexps format-types)}))]
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

;;
;; Ring
;;

(defn format-request [formats request]
  (let [[ctf ctc] (negotiate-request formats request)
        [af ac] (negotiate-response formats request)
        decoder (if (decode-request? formats request)
                  (decoder formats ctf))
        body (:body request)]
    (as-> request $
          (assoc $ ::accept af)
          (if (and body decoder)
            (try
              (-> $
                  (assoc ::adapter ctf)
                  (assoc :body nil)
                  (assoc :body-params (decoder body)))
              (catch Exception e
                (on-decode-exception e format $)))
            $))))

(defn format-response [formats request response]
  (if (encode-response? formats request response)
    (let [format (or (get (:consumes formats) (::content-type response))
                     (::accept request)
                     (default-format formats))]
      (if-let [encoder (encoder formats format)]
        (as-> response $
              (assoc $ ::adapter format)
              (update $ :body encoder)
              (if-not (get (:headers $) "Content-Type")
                (set-content-type $ (content-type formats format))
                $))
        response))
    response))

;;
;; customization
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

(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :extract-accept-charset-fn extract-accept-charset-ring
   :extract-accept-fn extract-accept-ring
   :decode? (constantly true)
   :encode? encode-collections-with-override
   :charset "utf-8"
   ;charsets #{"utf-8", "utf-16", "iso-8859-1"
   :adapters {:json {:format ["application/json" #"application/(.+\+)?json"]
                     :decoder [formats/make-json-decoder {:keywords? true}]
                     :encoder [formats/make-json-encoder]
                     :encode-protocol [formats/EncodeJson formats/encode-json]}
              :edn {:format ["application/edn" #"^application/(vnd.+)?(x-)?(clojure|edn)"]
                    :decoder [formats/make-edn-decoder]
                    :encoder [formats/make-edn-encoder]
                    :encode-protocol [formats/EncodeEdn formats/encode-edn]}
              :msgpack {:format ["application/msgpack" #"^application/(vnd.+)?(x-)?msgpack"]
                        :decoder [formats/make-msgpack-decoder {:keywords? true}]
                        :encoder [formats/make-msgpack-encoder]
                        :encode-protocol [formats/EncodeMsgpack formats/encode-msgpack]}
              :yaml {:format ["application/x-yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]
                     :decoder [formats/make-yaml-decoder {:keywords true}]
                     :encoder [formats/make-yaml-encoder]
                     :encode-protocol [formats/EncodeYaml formats/encode-yaml]}
              :transit-json {:format ["application/transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]
                             :decoder [(partial formats/make-transit-decoder :json)]
                             :encoder [(partial formats/make-transit-encoder :json)]
                             :encode-protocol [formats/EncodeTransitJson formats/encode-transit-json]}
              :transit-msgpack {:format ["application/transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]
                                :decoder [(partial formats/make-transit-decoder :msgpack)]
                                :encoder [(partial formats/make-transit-encoder :msgpack)]
                                :encode-protocol [formats/EncodeTransitMessagePack formats/encode-transit-msgpack]}}
   :formats [:json :edn :msgpack :yaml :transit-json :transit-msgpack]})

;;
;; Working with options
;;

(defn transform-adapter-options [f options]
  (update options :adapters #(into (empty %) (map (fn [[k v]] [k (f v)]) %))))

(def no-decoding (partial transform-adapter-options #(dissoc % :decoder)))
(def no-encoding (partial transform-adapter-options #(dissoc % :encoder)))

(def no-protocol-encoding
  (partial transform-adapter-options #(dissoc % :encode-protocol)))

(defn with-decoder-opts [options format opts]
  (assoc-in options [:adapters format :decoder-opts] opts))

(defn with-encoder-opts [options format opts]
  (assoc-in options [:adapters format :encoder-opts] opts))

(defn with-formats [options formats]
  (assoc options :formats formats))

;;
;; request helpers
;;

(defn disable-request-decoding [request]
  (assoc request ::adapter nil))

;;
;; response helpers
;;

(defn disable-response-encoding [response]
  (assoc response ::adapter nil))

(defn set-response-content-type [response content-type]
  (assoc response ::content-type content-type))
