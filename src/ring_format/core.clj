(ns ring-format.core
  (:require [clojure.string :as str]
            [ring-format.formats :as formats]
            [clojure.core.memoize :as memoize]))

(set! *warn-on-reflection* true)

;;
;; Content-type resolution
;;

(defn- content-type->format [formats]
  (reduce
    (fn [acc [k type]]
      (let [old-k (acc type)]
        (when (and old-k (not= old-k k))
          (throw (ex-info "content-type refers to multiple formats" {:content-type type
                                                                     :formats [k old-k]}))))
      (assoc acc type k))
    {}
    (for [[k type-or-types] formats
          :let [types (flatten (vector type-or-types))]
          type types
          :when (string? type)]
      [k type])))

(defn- format-regexps [formats]
  (reduce
    (fn [acc [k type]]
      (conj acc [k type]))
    []
    (for [[k type-or-types] formats
          :let [types (flatten (vector type-or-types))]
          type types
          :when (not (string? type))]
      [k type])))

(defn- format->content-type [formats]
  (reduce
    (fn [acc [k type]]
      (if-not (acc k)
        (assoc acc k type)
        acc))
    {}
    (for [[k type-or-types] formats
          :let [types (flatten (vector type-or-types))]
          type types
          :when (string? type)]
      [k type])))

(defn- match? [^String content-type string-or-regexp request]
  (and (:body request) (re-find string-or-regexp content-type)))

;; TODO: order and filter by formats!
(defn format-map [adapters]
  (let [formats (for [[k {:keys [format]}] adapters] [k format])]
    {:consumes (content-type->format formats)
     :produces (format->content-type formats)
     :matchers (format-regexps formats)}))

(defn extract-format [consumes matchers extract-content-type-fn request]
  (if-let [content-type (extract-content-type-fn request)]
    (or (get consumes content-type)
        (loop [i 0]
          (let [[f r] (nth matchers i)]
            (cond
              (match? content-type r request) f
              (< (inc i) (count matchers)) (recur (inc i))))))
    (nth matchers 1)))

(defn extract-accept-format [consumes extract-accept-fn request]
  (if-let [accept (extract-accept-fn request)]
    (or
      (get consumes accept)
      ;; TODO: remove ;-stuff
      (let [data (str/split accept #",\s*")]
        (loop [i 0]
          (or (get consumes (nth data i))
              (if (< (inc i) (count data))
                (recur (inc i)))))))))

;;
;; accept resolution
;;

(defn- old-sort-by-check
  [by check headers]
  (sort-by by (fn [a b]
                (cond (= (= a check) (= b check)) 0
                      (= a check) 1
                      :else -1))
           headers))

(defn- old-parse-accept-header*
  "Parse Accept headers into a sorted sequence of maps.
  \"application/json;level=1;q=0.4\"
  => ({:type \"application\" :sub-type \"json\"
       :q 0.4 :parameter \"level=1\"})"
  [accept-header]
  (->> (map (fn [val]
              (let [[media-range & rest] (str/split (str/trim val) #";")
                    type (zipmap [:type :sub-type]
                                 (str/split (str/trim media-range) #"/"))]
                (cond (nil? rest)
                      (assoc type :q 1.0)
                      (= (first (str/triml (first rest)))
                         \q)                                ;no media-range params
                      (assoc type :q
                                  (Double/parseDouble
                                    (second (str/split (first rest) #"="))))
                      :else
                      (assoc (if-let [q-val (second rest)]
                               (assoc type :q
                                           (Double/parseDouble
                                             (second (str/split q-val #"="))))
                               (assoc type :q 1.0))
                        :parameter (str/trim (first rest))))))
            (str/split accept-header #","))
       (old-sort-by-check :parameter nil)
       (old-sort-by-check :type "*")
       (old-sort-by-check :sub-type "*")
       (sort-by :q >)))

(def ^:private old-parse-accept-header
  "Memoized form of [[parse-accept-header*]]"
  (memoize/fifo old-parse-accept-header* :fifo/threshold 500))

(defn- old-accept-maps [extract-accept-fn request]
  (if-let [accept (extract-accept-fn request)]
    (if (string? accept)
      (old-parse-accept-header accept)
      accept)))

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

;;
;; customization
;;

(defn extract-content-type-default
  "Extracts content-type from request:
  1) [:content-type]
  2) [:headers \"content-type\"]
  3) [:headers [\"Content-Type\"]"
  [request]
  (or (:content-type request)
      (let [headers (:headers request)]
        (or
          (get headers "content-type")
          (get headers "Content-Type")))))

(defn extract-content-type-ring
  "Extracts content-type from ring-request."
  [request]
  (get (:headers request) "content-type"))

(defn extract-accept-ring
  "Extracts accept from ring-request."
  [request]
  (get (:headers request) "accept"))

(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :extract-accept-fn extract-accept-ring
   :adapters {:json {:format ["application/json" #"^application/(vnd.+)?json"]
                     :decoder [formats/make-json-decoder {:key-fn true}]
                     :encoder [formats/make-json-encoder]}
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

(format-map (:adapters default-options))

;;
;; spike
;;

(def +request+ {:headers {"content-type" "application/json", "accept" "application/json"} :body "kikka"})

(println "---- accept ----")

;; RMF
(let [extract-accept-fn (:extract-accept-fn default-options)]
  (time
    (dotimes [_ 1000000]
      (old-accept-maps extract-accept-fn +request+))))

;; NEW
(let [{:keys [extract-accept-fn adapters]} default-options
      {:keys [consumes]} (format-map adapters)]
  (time
    (dotimes [_ 1000000]
      (extract-accept-format consumes extract-accept-fn +request+))))

(println "---- content-type ----")

;; RMF
(let [json-request? (fn [{:keys [body] :as req}]
                      (if-let [^String type (get req :content-type
                                                 (get-in req [:headers "Content-Type"]
                                                         (get-in req [:headers "content-type"])))]
                        (and body (not (empty? (re-find #"^application/(vnd.+)?json" type))))))]
  (time
    (dotimes [_ 1000000]
      (json-request? +request+))))

;; NEW
(let [{:keys [extract-content-type-fn adapters]} default-options
      {:keys [consumes matchers]} (format-map adapters)]
  (time
    (dotimes [_ 1000000]
      (extract-format consumes matchers extract-content-type-fn +request+))))


;; NAIVE
(let [json-request? (fn [req] (and (= (get-in req [:headers "content-type"]) "application/json")
                                   (:body req)
                                   true))]
  (time
    (dotimes [_ 1000000]
      (json-request? +request+))))
