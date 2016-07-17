(ns ring.middleware.perf-test
  (:require [criterium.core :as cc]
            [cheshire.core :as json]
            [ring.middleware.format :as rmf]
            [clojure.java.io :as io]))

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

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(defn post-json* [app json]
  (->
    (app {:uri "/any"
          :request-method :post
          :content-type "application/json"
          :body (io/input-stream (.getBytes json))})
    :body
    slurp))

(defn parse [s] (json/parse-string s true))

(defn bench []
  (let [app (rmf/wrap-restful-format
              (fn [_] {:status 200 :body {:ping "pong"}}))
        data (json/generate-string {:kikka "kukka"})
        call #(post-json* app data)]

    (title "2-way JSON")
    (assert (= {:ping "pong"} (parse (call))))
    (cc/bench (call)))

  ; 33µs =>

  )

(comment
  (bench))

;[ring.middleware.format_params$eval34317$fn__34318 invoke "format_params.clj" 269]
;[ring.middleware.format_params$wrap_format_params2$fn__34233 invoke "format_params.clj" 209]
;[ring.middleware.format_params$eval34317 invokeStatic "format_params.clj" 269]
;[ring.middleware.format_params$eval34317 invoke "format_params.clj" 269]

;[ring.middleware.format_params$eval34429$fn__34430 invoke "format_params.clj" 269]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$wrap_format_params$fn__34361 invoke "format_params.clj" 118]
;[ring.middleware.format_params$eval34429 invokeStatic "format_params.clj" 269]
;[ring.middleware.format_params$eval34429 invoke "format_params.clj" 269]
