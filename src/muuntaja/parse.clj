(ns muuntaja.parse
  (:require [clojure.string :as str])
  (:import (com.fasterxml.jackson.databind.util LRUMap)))

;;
;; Utils
;;

(defn cache [n]
  (LRUMap. n n))

(defn fast-memoize [^LRUMap cache f]
  (fn [& args]
    (or (.get cache args)
        (let [ret (apply f args)]
          (when ret
            (.put cache args ret))
          ret))))

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
;; Parse accept (ported from ring-middleware-format)
;;

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
    (->> (map (fn [val]
                (let [[media-range & rest] (str/split (str/trim val) #";")
                      type {:type media-range}]
                  (cond (nil? rest)
                        (assoc type :q 1.0)
                        (= (first (str/triml (first rest)))
                           \q)                              ;no media-range params
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
         (sort-by-check :parameter nil)
         (sort-by-check :type "*/*")
         (sort-by :q >)
         (map :type))))

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
           (sort-by second)
           (reverse)
           (map first)
           (map str/lower-case)))))
