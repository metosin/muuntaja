(ns muuntaja.json-perf-test
  (:require [criterium.core :as cc]
            [clojure.test :refer :all]
            [muuntaja.test_utils :refer :all]
            [muuntaja.json :as json]
            [muuntaja.jackson :as jackson]
            [cheshire.core :as cheshire])
  (:import [java.util Map]))

(set! *warn-on-reflection* true)

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(deftest encode-json
  (is (= "{\"list\":[42,true]}"
         (.toString
           (doto (json/object)
             (.put "list" (doto (json/array)
                            (.add 42)
                            (.add true))))))))

(def ^String +json+ (cheshire.core/generate-string {"hello" "world"}))

(def +data+ (cheshire.core/parse-string +json+))

(defn encode-perf []

  ;; 1005ns
  (title "encode: cheshire")
  (let [encode (fn [] (cheshire/generate-string {"hello" "world"}))]
    (assert (= +json+ (encode)))
    (cc/bench (encode)))

  ;; 183ns
  (title "encode: muuntaja.json")
  (let [encode (fn [] (str (doto (json/object) (.put "hello" "world"))))]
    (assert (= +json+ (encode)))
    (cc/bench (encode)))

  ;; 193ns
  (title "encode: muuntaja.jackson")
  (let [encode (fn [] (jackson/to-json {"hello" "world"}))]
    (assert (= +json+ (encode)))
    (cc/bench (encode)))

  ;; 82ns
  (title "encode: str")
  (let [encode (fn [] (str "{\"hello\":\"" "world" "\"}"))]
    (assert (= +json+ (encode)))
    (cc/bench (encode))))

(defn decode-perf []

  ;; 896ns
  (title "decode: cheshire")
  (let [decode (fn [] (cheshire/parse-string-strict +json+))]
    (assert (= +data+ (decode)))
    (cc/bench (decode)))

  ;; 319ns
  (title "decode: muuntaja.json")
  (let [decode (fn [] (json/decode-map +json+))]
    (assert (= +data+ (decode)))
    (cc/bench (decode)))

  ;; 464ns
  (title "decode: muuntaja.jackson")
  (let [decode (fn [] (jackson/from-json +json+))]
    (assert (= +data+ (decode)))
    (cc/bench (decode)))

  ;; 246ns
  (title "decode: jackson")
  (let [decode (fn [] (.readValue json/mapper +json+ Map))]
    (assert (= +data+ (decode)))
    (cc/bench (decode))))

(comment
  (encode-perf)
  (decode-perf))
