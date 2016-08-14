(ns muuntaja.core
  (:require [clojure.string :as str]
            [muuntaja.formats :as formats])
  (:refer-clojure :exclude [compile]))

(set! *warn-on-reflection* true)

(defn- match? [^String content-type string-or-regexp request]
  (and (:body request) (re-find string-or-regexp content-type)))

(defprotocol RequestFormatter
  (extract-content-type-format [_ request])
  (extract-accept-format [_ request]))

(defprotocol Formatter
  (encoder [_ format])
  (decoder [_ format])
  (default-format [_]))

(defrecord Adapter [encode decode binary?])

(defrecord Formats [extract-content-type-fn extract-accept-fn encode-body-fn encode-error-fn consumes matchers adapters formats default-format]
  RequestFormatter
  (extract-content-type-format [_ request]
    (if-let [content-type (extract-content-type-fn request)]
      (or (get consumes content-type)
          (loop [i 0]
            (let [[f r] (nth matchers i)]
              (cond
                (match? content-type r request) f
                (< (inc i) (count matchers)) (recur (inc i))))))
      (nth matchers 1)))

  ;; TODO: really parse ;-stuff, memoized fn?
  (extract-accept-format [_ request]
    (if-let [accept (extract-accept-fn request)]
      (or
        (get consumes accept)
        (let [data (str/split accept #",\s*")]
          (loop [i 0]
            (or (get consumes (nth data i))
                (if (< (inc i) (count data))
                  (recur (inc i)))))))))

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

(defn- encode-response-body? [formats request response]
  (if-let [f (:encode-body-fn formats)]
    (f request response)))

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
  (let [content-type (extract-content-type-format formats request)
        accept (extract-accept-format formats request)
        decoder (decoder formats content-type)
        body (:body request)]
    (as-> request $
          (assoc $ ::request content-type)
          (assoc $ ::response accept)
          (if decoder
            (try
              (-> $
                  (assoc :body nil)
                  (assoc :body-params (decoder body)))
              (catch Exception e
                (if-let [f (:encode-exception-fn formats)]
                  (f e content-type $)
                  (throw e))))
            request))))

(defn format-response [formats request response]
  (if-not (::format response)
    (if-let [format (or (::encode response)
                        (::response request)
                        (default-format formats))]
      (if (encode-response-body? formats request response)
        (if-let [encoder (encoder formats format)]
          (-> response
              (assoc ::format format)
              (update :body encoder))
          response)
        response)
      response)
    response))

(defn wrap-format [handler options]
  (let [formats (compile options)]
    (fn
      ([request]
       (let [format-request (format-request formats request)]
         (->> (handler format-request)
              (format-response formats format-request))))
      ([request respond raise]
       (let [format-request (format-request formats request)]
         (handler format-request #(respond (format-response formats format-request %)) raise))))))

;;
;; Interceptors
;;

(defrecord Interceptor [enter leave])

(defn format-interceptor [options]
  (let [formats (compile options)]
    (map->Interceptor
      {:enter (fn [ctx]
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

(defn default-handle-exception [^Exception e format request]
  (throw
    (ex-info
      (format "Malformed %s request." format)
      {:type ::decode
       :format format
       :request request}
      e)))

(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :extract-accept-fn extract-accept-ring
   :encode-body-fn encode-collections
   :encode-exception-fn default-handle-exception
   :charset "utf-8"
   :adapters {:json {:format ["application/json" #"^application/(vnd.+)?json"]
                     :decoder [formats/make-json-decoder {:keywords? true}]
                     :encoder [formats/make-json-encoder]
                     :binary? true}
              :edn {:format ["application/edn" #"^application/(vnd.+)?(x-)?(clojure|edn)"]
                    :decoder [formats/make-edn-decoder]
                    :encoder [formats/make-edn-encoder]}
              :msgpack {:format ["application/msgpack" #"^application/(vnd.+)?(x-)?msgpack"]
                        :decoder [formats/make-msgpack-decoder]
                        :encoder [formats/make-msgpack-encoder]
                        :binary? true}
              :yaml {:format ["application/yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]
                     :decoder [formats/make-yaml-decoder {:keywords true}]
                     :encoder [formats/make-yaml-encoder]}
              :transit-json {:format ["application/transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]
                             :decoder [(partial formats/make-transit-decoder :json)]
                             :encoder [(partial formats/make-transit-encoder :json)]
                             :binary? true}
              :transit-msgpack {:format ["application/transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]
                                :decoder [(partial formats/make-transit-decoder :msgpack)]
                                :encoder [(partial formats/make-transit-encoder :msgpack)]
                                :binary? true}}
   :formats [:json :edn :msgpack :yaml :transit-json :transit-msgpack]})

(defn transform-adapter-options [f options]
  (update options :adapters #(into (empty %) (map (fn [[k v]] [k (f v)]) %))))

(def no-decoding (partial transform-adapter-options #(dissoc % :decoder)))
(def no-encoding (partial transform-adapter-options #(dissoc % :encoder)))
