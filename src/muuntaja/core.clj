(ns muuntaja.core
  (:require [clojure.string :as str]
            [muuntaja.formats :as formats])
  (:refer-clojure :exclude [compile]))

(declare default-options)

(defn- match? [^String content-type string-or-regexp request]
  (and (:body request) (re-find string-or-regexp content-type)))

(defn- assoc-assoc [m k1 k2 v]
  (assoc m k1 (assoc (k1 m) k2 v)))

(defn- stripped [^String s]
  (if s
    (let [i (.indexOf s ";")]
      (if (neg? i) s (.substring s 0 i)))))

(defn- on-decode-exception [^Exception e format request]
  (throw
    (ex-info
      (str "Malformed " format " request.")
      {:type ::decode
       :format format
       :request request}
      e)))

(defn- content-type [response content-type]
  (assoc-assoc response :headers "Content-Type" content-type))

(defprotocol RequestFormatter
  (extract-content-type-format [_ request])
  (extract-accept-format [_ request])
  (decode-request? [_ request])
  (encode-response? [_ request response]))

(defprotocol Formatter
  (encoder [_ format])
  (decoder [_ format])
  (default-format [_]))

(defrecord Adapter [encode decode binary?])

(defrecord Formats [extract-content-type-fn
                    extract-accept-fn
                    encode?
                    decode?
                    encode-error-fn
                    consumes
                    matchers
                    adapters
                    formats
                    default-format]
  RequestFormatter
  (extract-content-type-format [_ request]
    (if-let [content-type (stripped (extract-content-type-fn request))]
      (or (get consumes content-type)
          (loop [i 0]
            (let [[f r] (nth matchers i)]
              (cond
                (match? content-type r request) f
                (< (inc i) (count matchers)) (recur (inc i))))))))

  (extract-accept-format [_ request]
    (if-let [accept (extract-accept-fn request)]
      (or
        (get consumes accept)
        (let [data (str/split accept #",\s*")]
          (loop [i 0]
            (or (get consumes (stripped (nth data i)))
                (if (< (inc i) (count data))
                  (recur (inc i)))))))))

  (decode-request? [_ request]
    (and decode?
         (not (contains? request ::adapter))
         (decode? request)))

  (encode-response? [_ request response]
    (and encode?
         (not (contains? response ::adapter))
         (encode? request response)))

  Formatter
  (encoder [_ format]
    (-> format adapters :encode))

  (decoder [_ format]
    (-> format adapters :decode))

  (default-format [_]
    default-format))

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

(defn- format->content-type [format-types charset]
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
      [k (str type "; charset=" charset)])))

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
                               (select-keys adapter [:binary?])
                               (if decoder {:decode (make decoder decoder-opts nil)})
                               (if encoder {:encode (make encoder encoder-opts encode-protocol)})))])))
         (into {}))))

(defn compile [{:keys [adapters formats charset] :as options}]
  (let [selected-format? (set formats)
        format-types (for [[k {:keys [format]}] adapters
                           :when (selected-format? k)]
                       [k format])
        adapters (compile-adapters adapters formats)]
    (map->Formats
      (merge
        options
        {:default-format (first formats)
         :adapters adapters
         :consumes (content-type->format format-types)
         :produces (format->content-type format-types charset)
         :matchers (format-regexps format-types)}))))

;;
;; Ring
;;

(defn format-request [formats request]
  (let [content-type-format (extract-content-type-format formats request)
        accept-format (extract-accept-format formats request)
        decoder (if (decode-request? formats request)
                  (decoder formats content-type-format))
        body (:body request)]
    (as-> request $
          (assoc $ ::accept accept-format)
          (if (and body decoder)
            (try
              (-> $
                  (assoc ::adapter content-type-format)
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
                (content-type $ ((:produces formats) format))
                $))
        response))
    response))

(defn wrap-format
  ([handler]
   (wrap-format handler default-options))
  ([handler options]
   (let [formats (compile options)]
     (fn
       ([request]
        (let [req (format-request formats request)]
          (->> (handler req) (format-response formats req))))
       ([request respond raise]
        (let [req (format-request formats request)]
          (handler req #(respond (format-response formats req %)) raise)))))))

;;
;; Ring-exceptions
;;

; [^Exception e format request]
(defn- default-on-exception [_ format _]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "Malformed " format " request.")})

(defn- try-catch [handler request on-exception]
  (try
    (handler request)
    (catch Exception e
      (if-let [data (ex-data e)]
        (if (-> data :type (= ::decode))
          (on-exception e (:format data) request)
          (throw e))
        (throw e)))))

(defn wrap-exception
  ([handler]
   (wrap-exception handler default-on-exception))
  ([handler on-exception]
   (fn [request]
     (try-catch handler request on-exception))))

;;
;; Interceptors
;;

(defrecord Interceptor [name enter leave])

(defn format-interceptor [options]
  (let [formats (compile options)]
    (map->Interceptor
      {:name ::format
       :enter (fn [ctx]
                (update ctx :request (partial format-request formats)))
       :leave (fn [ctx]
                (update ctx :response (partial format-response formats (:request ctx))))})))

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

(defn encode-collections [_ response]
  (-> response :body coll?))

(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :extract-accept-fn extract-accept-ring
   :decode? (constantly true)
   :encode? encode-collections
   :charset "utf-8"
   :adapters {:json {:format ["application/json" #"^application/(vnd.+)?json"]
                     :decoder [formats/make-json-decoder {:keywords? true}]
                     :encoder [formats/make-json-encoder]
                     :encode-protocol [formats/EncodeJson formats/encode-json]
                     :binary? true}
              :edn {:format ["application/edn" #"^application/(vnd.+)?(x-)?(clojure|edn)"]
                    :decoder [formats/make-edn-decoder]
                    :encoder [formats/make-edn-encoder]
                    :encode-protocol [formats/EncodeEdn formats/encode-edn]
                    :binary? true}
              :msgpack {:format ["application/msgpack" #"^application/(vnd.+)?(x-)?msgpack"]
                        :decoder [formats/make-msgpack-decoder]
                        :encoder [formats/make-msgpack-encoder]
                        :encode-protocol [formats/EncodeMsgpack formats/encode-msgpack]
                        :binary? true}
              :yaml {:format ["application/x-yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]
                     :decoder [formats/make-yaml-decoder {:keywords true}]
                     :encoder [formats/make-yaml-encoder]
                     :encode-protocol [formats/EncodeYaml formats/encode-yaml]}
              :transit-json {:format ["application/transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]
                             :decoder [(partial formats/make-transit-decoder :json)]
                             :encoder [(partial formats/make-transit-encoder :json)]
                             :encode-protocol [formats/EncodeTransitJson formats/encode-transit-json]
                             :binary? true}
              :transit-msgpack {:format ["application/transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]
                                :decoder [(partial formats/make-transit-decoder :msgpack)]
                                :encoder [(partial formats/make-transit-encoder :msgpack)]
                                :encode-protocol [formats/EncodeTransitMessagePack formats/encode-transit-msgpack]
                                :binary? true}}
   :formats [:json :edn :msgpack :yaml :transit-json :transit-msgpack]})

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
