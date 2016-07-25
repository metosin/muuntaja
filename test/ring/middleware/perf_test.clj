(ns ring.middleware.perf-test
  (:require [criterium.core :as cc]
            [cheshire.core :as json]
            [ring.middleware.format :as rmf]
            [ring.middleware.format-params :as rmfp]
            [ring.middleware.format-response :as rmfr])
  (:import [java.io ByteArrayInputStream]))

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

(set! *warn-on-reflection* true)

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(defn json-bytes [data]
  (.getBytes ^String (json/generate-string data)))

(defn post-body->stream [app datab]
  (->
    (app {:uri "/any"
          :request-method :post
          :content-type "application/json"
          :body (ByteArrayInputStream. datab)})
    :body
    slurp))

(defn post-body->data [app datab]
  (->
    (app {:uri "/any"
          :request-method :post
          :content-type "application/json"
          :body (ByteArrayInputStream. datab)})
    :body))

(defn post-params->stream [app data]
  (->
    (app {:uri "/any"
          :request-method :post
          :content-type "application/json"
          :body-params data})
    :body
    slurp))

(defn parse [s] (json/parse-string s true))

(def +data+ {:kikka "kukka"})
(def +data-bytes+ (json-bytes +data+))
(def +handler+ (fn [{data :body-params}] {:status 200 :body data}))

;;
;; naive
;;

(defn naive []

  ; 7.6µs => 4.6µs
  (let [app ((fn [handler]
               (fn [request]
                 (handler (assoc request :body-params (json/parse-string (slurp (:body request)) true)))))
              +handler+)
        call #(post-body->data app +data-bytes+)]

    (title "NAIVE JSON request")
    (assert (= +data+ (call)))
    (cc/bench (call)))

  ; 5.4µs => 4.6µs
  (let [app ((fn [handler]
               (fn [request]
                 (update
                   (handler request)
                   :body
                   #(ByteArrayInputStream. (json-bytes %)))))
              +handler+)
        call #(post-params->stream app +data+)]

    (title "NAIVE JSON response")
    (assert (= +data+ (parse (call))))
    (cc/bench (call)))

  ; 12.5µs => 9.4µs
  (let [app ((fn [handler]
               (fn [request]
                 (update
                   (handler (assoc request :body-params (json/parse-string (slurp (:body request)) true)))
                   :body
                   #(ByteArrayInputStream. (json-bytes %)))))
              +handler+)
        call #(post-body->stream app +data-bytes+)]

    (title "NAIVE JSON request & response")
    (assert (= +data+ (parse (call))))
    (cc/bench (call)))

  ; 9.0µs
  (let [app (fn [request] {:status 200 :body (-> request :body slurp (json/parse-string true) json-bytes (ByteArrayInputStream.))})
        call #(post-body->stream app +data-bytes+)]

    (title "SUPER NAIVE JSON request & response")
    (assert (= +data+ (parse (call))))
    (cc/bench (call))))

;;
;; Real
;;

(defn wrap-api-params []
  ; 10.7µs
  ;  8.9µs (-17%), cleanup
  ;  3.2µs (-64%), charset default (-70%)
  (let [app (rmfp/wrap-api-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        call #(post-body->data app +data-bytes+)]

    (title "JSON request")
    (assert (= +data+ (call)))
    (cc/bench (call))))

(defn wrap-api-response []
  ; 14.5µs
  ; 12.2µs (-16%), cleanup
  ; 12.2µs (-0%), charset default
  ;  8.2µs (-26%), lu => fifo (-43%)
  (let [app (rmfr/wrap-api-response +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        call #(post-params->stream app +data+)]

    (title "JSON response")
    (assert (= +data+ (parse (call))))
    (cc/bench (call))))

(defn wrap-api-format []
  ; 28.7µs
  ; 25.3µs (-12%), cleanup
  ; 17.3µs (-32%), charset default
  ; 11,9µs (-31%), lu => fifo (-58%)
  (let [app (rmf/wrap-api-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        call #(post-body->stream app +data-bytes+)]

    (title "JSON request & response")
    (assert (= +data+ (parse (call))))
    (cc/bench (call))))

(defn api []
  (wrap-api-format)
  (wrap-api-params)
  (wrap-api-response))

(comment
  (wrap-api-format)
  (wrap-api-params)
  (wrap-api-response)
  (naive))
