(ns muuntaja.parse
  (:require [clojure.string :as str])
  (:import (java.util.concurrent ConcurrentHashMap)))

;;
;; Cache
;;

(defn fast-memoize [size f]
  (let [cache (ConcurrentHashMap. size 0.8 4)
        sentinel (Object.)
        cache! (fn [args]
                 (let [value (apply f args)]
                   (when value
                     ;; not synchronized but ok enough
                     (when (> (.size cache) size)
                       (.clear cache))
                     (.putIfAbsent cache args value))
                   value))]
    (fn [& args]
      (let [cached (.getOrDefault cache args sentinel)]
        (if (identical? sentinel cached)
          (cache! args)
          cached)))))

;;
;; Parse content-type
;;

(defn- extract-charset [^String s]
  (if (.startsWith s "charset=")
    (.trim (subs s 8))))

(defn parse-content-type [^String s]
  (let [i (.indexOf s ";")]
    (if (neg? i)
      [s nil]
      [(.substring s 0 i) (extract-charset (.toLowerCase (.trim (.substring s (inc i)))))])))

;;
;; Parse accept (ported from ring-middleware-format and liberator)
;; https://github.com/clojure-liberator/liberator/blob/master/src/liberator/conneg.clj#L13
;;

(def ^:private accept-fragment-re
  #"^\s*((\*|[^()<>@,;:\"/\[\]?={}         ]+)/(\*|[^()<>@,;:\"/\[\]?={}         ]+))$")

(def ^:private accept-fragment-param-re
  #"([^()<>@,;:\"/\[\]?={} 	]+)=([^()<>@,;:\"/\[\]?={} 	]+|\"[^\"]*\")$")

(defn- parse-q [s]
  (try
    (->> (Double/parseDouble s)
         (min 1)
         (max 0))
    (catch NumberFormatException e
      nil)))

(defn- sort-by-check
  [by check headers]
  (sort-by by (fn [a b]
                (cond (= (= a check) (= b check)) 0
                      (= a check) 1
                      :else -1))
           headers))

(defn parse-accept
  "Parse Accept headers into a sorted sequence of content-types.
  \"application/json;level=1;q=0.4\"
  => (\"application/json\"})"
  [accept-header]
  (if accept-header
    (->> (map (fn [fragment]
                (let [[media-range & params-list] (str/split fragment #"\s*;\s*")
                      type (second (re-matches accept-fragment-re media-range))]
                  (-> (reduce (fn [m s]
                                (if-let [[k v] (seq (rest (re-matches accept-fragment-param-re s)))]
                                  (if (= "q" k)
                                    (update-in m [:q] #(or % (parse-q v)))
                                    (assoc m (keyword k) v))
                                  m))
                              {:type type}
                              params-list)
                      (update-in [:q] #(or % 1.0)))))
              (str/split accept-header #"[\s\n\r]*,[\s\n\r]*"))
         (sort-by-check :type "*/*")
         (sort-by :q >)
         (map :type))))

(defn- reverse-compare [x y] (compare y x))

(defn parse-accept-charset [^String s]
  (if s
    (let [segments (str/split s #",")
          choices (for [segment segments
                        :when (not (empty? segment))
                        :let [[_ charset qs] (re-find #"([^;]+)(?:;\s*q\s*=\s*([0-9\.]+))?" segment)]
                        :when charset
                        :let [qscore (try
                                       (Double/parseDouble (str/trim qs))
                                       (catch Exception e 1))]]
                    [(str/trim charset) qscore])]
      (->> choices
           (sort-by second reverse-compare)
           (map first)
           (map str/lower-case)))))
