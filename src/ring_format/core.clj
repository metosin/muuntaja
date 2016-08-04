(ns ring-format.core
  (:require [clojure.string :as str]
            [ring-format.adapters :as adapters]
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
  (and (:body request)
       (re-find string-or-regexp content-type)))

(defn parse-formats [formats]
  {:lookup (content-type->format formats)
   :defaults (format->content-type formats)
   :matchers (format-regexps formats)})

(defn extract-format [lookup matchers extract-content-type-fn request]
  (if-let [content-type (extract-content-type-fn request)]
    (or (get lookup content-type)
        (loop [i 0]
          (let [[f r] (nth matchers i)]
            (cond
              (match? content-type r request) f
              (< (inc i) (count matchers)) (recur (inc i))))))
    (nth matchers 1)))

(defn extract-accept-format [lookup extract-accept-fn request]
  (if-let [accept (extract-accept-fn request)]
    (or
      (get lookup accept)
      ;; TODO: remove ;-stuff
      (let [data (str/split accept #",\s*")]
        (loop [i 0]
          (or (get lookup (nth data i))
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
   :formats [[:json ["application/json" #"^application/(vnd.+)?json"]]
             [:edn ["application/edn" "application/clojure" #"^application/(vnd.+)?(x-)?(clojure|edn)"]]
             [:msgpack ["application/msgpack" "application/x-msgpack" #"^application/(vnd.+)?(x-)?msgpack"]]
             [:yaml ["application/yaml" "application/x-yaml" "text/yaml" "text/x-yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]]
             [:transit-json ["application/transit+json" "application/x-transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]]
             [:transit-msgpack ["application/transit+msgpack" "application/x-transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]]]
   :adapters adapters/default-adapters})

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
(let [{:keys [extract-accept-fn formats]} default-options
      {:keys [lookup]} (parse-formats formats)]
  (time
    (dotimes [_ 1000000]
      (extract-accept-format lookup extract-accept-fn +request+))))

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
(let [{:keys [extract-content-type-fn formats]} default-options
      {:keys [lookup matchers]} (parse-formats formats)]
  (time
    (dotimes [_ 1000000]
      (extract-format lookup matchers extract-content-type-fn +request+))))


;; NAIVE
(let [json-request? (fn [req] (and (= (get-in req [:headers "content-type"]) "application/json")
                                   (:body req)
                                   true))]
  (time
    (dotimes [_ 1000000]
      (json-request? +request+))))
