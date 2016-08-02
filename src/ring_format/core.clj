(ns ring-format.core)

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

(def default-options
  {:extract-content-type-fn extract-content-type-ring
   :formats [[:json ["application/json" #"^application/(vnd.+)?json"]]
             [:edn ["application/edn" "application/clojure" #"^application/(vnd.+)?(x-)?(clojure|edn)"]]
             [:msgpack ["application/msgpack" "application/x-msgpack" #"^application/(vnd.+)?(x-)?msgpack"]]
             [:yaml ["application/yaml" "application/x-yaml" "text/yaml" "text/x-yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]]
             [:transit-json ["application/transit+json" "application/x-transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]]
             [:transit-msgpack ["application/transit+msgpack" "application/x-transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]]]})

;;
;; spike
;;

(def +request+ {:headers {"Content-Type" "application/json"} :body "kikka"})

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
(let [{:keys [lookup matchers]} (parse-formats default-options)]
  (time
    (dotimes [_ 1000000]
      (extract-format lookup matchers extract-content-type-ring +request+))))


;; NAIVE
(let [json-request? (fn [req] (and (= (get-in req [:headers "content-type"]) "application/json")
                                   (:body req)
                                   true))]
  (time
    (dotimes [_ 1000000]
      (json-request? +request+))))
