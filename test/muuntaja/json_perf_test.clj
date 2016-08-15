(ns muuntaja.json-perf-test
  (:require [criterium.core :as cc]
            [clojure.test :refer :all]
            [muuntaja.test_utils :refer :all]
            [muuntaja.json :as json]
            [cheshire.core :as cheshire]))

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

(defn bench []

  ;; 1005ns
  (title "encode: cheshire")
  (let [encode (fn [] (cheshire/generate-string {"hello" "world"}))]
    (assert (= "{\"hello\":\"world\"}" (encode)))
    (cc/quick-bench (encode)))

  ;; 183ns
  (title "encode: muuntaja.json")
  (let [encode (fn [] (.toString (doto (json/object) (.put "hello" "world"))))]
    (assert (= "{\"hello\":\"world\"}" (encode)))
    (cc/quick-bench (encode)))

  ;; 82ns
  (title "encode: str")
  (let [encode (fn [] (str "{\"hello\":\"" "world" "\"}"))]
    (assert (= "{\"hello\":\"world\"}" (encode)))
    (cc/quick-bench (encode))))

(comment
  (bench))
