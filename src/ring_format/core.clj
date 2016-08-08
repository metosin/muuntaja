(ns ring-format.core
  (:require [clojure.string :as str]
            [ring-format.formats :as formats])
  (:refer-clojure :exclude [compile]))

(set! *warn-on-reflection* true)

(defn- match? [^String content-type string-or-regexp request]
  (and (:body request) (re-find string-or-regexp content-type)))

(defprotocol FormatExtractor
  (extract-content-type-format [_ request])
  (extract-accept-format [_ request])
  (default-format [_]))

(defprotocol Formatter
  (encoder [_ format])
  (decoder [_ format]))

(defrecord Formats [extract-content-type-fn extract-accept-fn encode-body-fn encode-error-fn consumes matchers adapters formats default-format]
  FormatExtractor
  (extract-content-type-format [_ request]
    (if-let [content-type (extract-content-type-fn request)]
      (or (get consumes content-type)
          (loop [i 0]
            (let [[f r] (nth matchers i)]
              (cond
                (match? content-type r request) f
                (< (inc i) (count matchers)) (recur (inc i))))))
      (nth matchers 1)))

  (extract-accept-format [_ request]
    (if-let [accept (extract-accept-fn request)]
      (or
        (get consumes accept)
        ;; TODO: remove ;-stuff
        (let [data (str/split accept #",\s*")]
          (loop [i 0]
            (or (get consumes (nth data i))
                (if (< (inc i) (count data))
                  (recur (inc i)))))))))

  (default-format [_]
    default-format)

  Formatter
  (encoder [_ format]
    (-> format adapters :encode))

  (decoder [_ format]
    (-> format adapters :decode)))

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

(defn- encode-response-body? [formats request response]
  (if-let [f (:encode-body-fn formats)]
    (f request response)))

(defn- make-adapters [adapters formats]
  (let [make (fn [spec spec-opts]
               (if (vector? spec)
                 (let [[f opts] spec]
                   (f (merge opts spec-opts)))
                 spec))]
    (->> formats
         (keep identity)
         (mapv (fn [format]
                 (if-let [{:keys [decoder decoder-opts encoder encoder-opts] :as adapter}
                          (if (map? format) format (get adapters format))]
                   [format (merge
                             (select-keys adapter [:binary?])
                             (if decoder {:decode (make decoder decoder-opts)})
                             (if encoder {:encode (make encoder encoder-opts)}))])))
         (into {}))))

(defn compile [{:keys [adapters formats] :as options}]
  (let [selected-format? (set formats)
        format-types (for [[k {:keys [format]}] adapters
                           :when (selected-format? k)]
                       [k format])
        adapters (make-adapters adapters formats)]
    (map->Formats
      (merge
        options
        {:default-format (first formats)
         :adapters adapters
         :consumes (content-type->format format-types)
         :produces (format->content-type format-types)
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
  (if-let [format (or (::format response)
                      (::response request)
                      (default-format formats))]
    (if (encode-response-body? formats request response)
      (if-let [encoder (encoder formats format)]
        (as-> response $
              (assoc $ ::format format)
              (update $ :body encoder))
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

;; TODO: `:charset`
(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :extract-accept-fn extract-accept-ring
   :encode-body-fn encode-collections
   :encode-exception-fn default-handle-exception
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
