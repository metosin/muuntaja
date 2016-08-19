(ns muuntaja.core-perf-test
  (:require [criterium.core :as cc]
            [muuntaja.core :as muuntaja]
            [muuntaja.json :as json]
            [muuntaja.test_utils :refer :all]
            [cheshire.core :as cheshire]
            [ring.middleware.format-params :as ring-middleware-formatp]
            [ring.middleware.format :as ring-middleware-format]
            [ring.middleware.json :as rmj]
            [muuntaja.formats :as formats]))

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

(def +json-request+
  {:headers {"content-type" "application/json"
             "accept" "application/json"}
   :body "{\"kikka\":42}"})

(def +transit-json-request+
  {:headers {"content-type" "application/transit+json"
             "accept" "application/transit+json"}
   :body "[\"^ \",\"~:kikka\",42]"})

(defrecord Hello [^String name]
  formats/EncodeJson
  (encode-json [_]
    (str (doto (json/object) (.put "hello" name)))))

(def +handler+ (fn [request] {:status 200 :body (:body-params request)}))
(def +handler2+ (fn [_] {:status 200 :body (->Hello "yello")}))

(defn handle [context handler]
  (assoc context :response (handler (:request context))))

(defrecord Context [request])

;;
;; naive
;;

(defn old []

  (let [match? (fn [regexp]
                 (fn [{:keys [body] :as req}]
                   (if-let [^String type (get req :content-type
                                              (get-in req [:headers "Content-Type"]
                                                      (get-in req [:headers "content-type"])))]
                     (and body (not (empty? (re-find regexp type)))))))
        api-request? (some-fn
                       (match? #"^application/(vnd.+)?json")
                       (match? #"^application/(vnd.+)?(x-)?(clojure|edn)")
                       (match? #"^application/(vnd.+)?(x-)?msgpack")
                       (match? #"^(application|text)/(vnd.+)?(x-)?yaml")
                       (match? #"^application/(vnd.+)?(x-)?transit\+json")
                       (match? #"^application/(vnd.+)?(x-)?transit\+msgpack"))]

    ;; 551ns
    (title "ring-middleware-format: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 2520ns
    (title "ring-middleware-format: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+)))

  (let [match? (fn [type]
                 (fn [req] (and (= (get-in req [:headers "content-type"]) type)
                                (:body req)
                                true)))
        api-request? (some-fn
                       (match? "application/json")
                       (match? "application/edn")
                       (match? "application/msgpack")
                       (match? "application/yaml")
                       (match? "application/transit+json")
                       (match? "application/transi+msgpack"))]

    ;; 93ns
    (title "NAIVE: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 665ns
    (title "NAIVE: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+))))

;;
;; Real
;;

(defn content-type []
  (let [formats (-> muuntaja/default-options muuntaja/no-decoding muuntaja/no-encoding muuntaja/compile)]

    ; 52ns
    ; 38ns consumes & produces (-27%)
    ; 27ns compile (-29%) (-48%)
    (title "Content-type: JSON")
    (assert (= :json (muuntaja/extract-content-type-format formats +json-request+)))
    (cc/quick-bench
      (muuntaja/extract-content-type-format formats +json-request+))

    ; 65ns
    ; 55ns consumes & produces (-15%)
    ; 42ns compile (-24%) (-35%)
    (title "Content-type: TRANSIT")
    (assert (= :transit-json (muuntaja/extract-content-type-format formats +transit-json-request+)))
    (cc/quick-bench
      (muuntaja/extract-content-type-format formats +transit-json-request+))))

(defn accept []
  (let [formats (-> muuntaja/default-options muuntaja/no-decoding muuntaja/no-encoding muuntaja/compile)]

    ; 71ns
    ; 58ns consumes & produces (-18%)
    ; 48ns compile (-17%) (-32%)
    (title "Accept: TRANSIT")
    (assert (= :transit-json (muuntaja/extract-accept-format formats +transit-json-request+)))
    (cc/quick-bench
      (muuntaja/extract-accept-format formats +transit-json-request+))))

(defn request []
  (let [formats (-> muuntaja/default-options muuntaja/no-decoding muuntaja/no-encoding muuntaja/compile)]

    ; 179ns
    ; 187ns (records)
    (title "Accept & Contnet-type: JSON")
    (cc/quick-bench
      (muuntaja/format-request formats +json-request+))

    ; 211ns
    ; 226ns (records)
    (title "Accept & Contnet-type: Transit")
    (cc/quick-bench
      (muuntaja/format-request formats +transit-json-request+))))

(defn parse-json []

  ; 2.0µs
  (title "parse-json-stream")
  (let [parse (muuntaja/decoder (muuntaja/compile muuntaja/default-options) :json)
        request! (request-stream +json-request+)]
    (cc/quick-bench (parse (:body (request!)))))

  ; 4.3µs
  (title "parse-json-string")
  (let [parse #(cheshire/parse-string % true)
        request! (request-stream +json-request+)]
    (cc/quick-bench (parse (slurp (:body (request!)))))))

(defn ring-middleware-format-e2e []

  ; 10.2µs
  (let [app (ring-middleware-formatp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 8.5µs
  (let [app (ring-middleware-formatp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 22.5µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (slurp (:body (app (request!))))))
    (cc/quick-bench (app (request!))))

  ; 21.0µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (slurp (:body (app (request!))))))
    (cc/quick-bench (app (request!)))))

(defn ring-json-e2e []
  (let [+handler+ (fn [request] {:status 200 :body (:body request)})]

    ; 5.2µs
    (let [app (rmj/wrap-json-body +handler+ {:keywords? true})
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST")
      (assert (= {:kikka 42} (:body (app (request!)))))
      (cc/quick-bench (app (request!))))

    ; 7.7µs
    (let [app (-> +handler+ (rmj/wrap-json-body {:keywords? true}) (rmj/wrap-json-response))
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST-RESPONSE")
      (assert (= (:body +json-request+) (:body (app (request!)))))
      (cc/quick-bench (app (request!))))))

(defn muuntaja-e2e []

  ; 2.3µs
  (let [app (muuntaja/wrap-format +handler+ (-> muuntaja/default-options muuntaja/no-encoding))
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 3.6µs
  (let [app (muuntaja/wrap-format +handler+ (-> muuntaja/default-options muuntaja/no-encoding))
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 3.6µs
  (let [app (muuntaja/wrap-format +handler+ muuntaja/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 7.1µs
  (let [app (muuntaja/wrap-format +handler+ muuntaja/default-options)
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (slurp (:body (app (request!))))))
    (cc/quick-bench (app (request!))))

  ; 3.8µs
  ; 2.6µs Protocol (-30%)
  (let [app (muuntaja/wrap-format +handler2+ muuntaja/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE (PROTOCOL)")
    (assert (= "{\"hello\":\"yello\"}" (:body (app (request!)))))
    (cc/quick-bench (app (request!)))))

(defn interceptor-e2e []

  ; 3.8µs
  (let [{:keys [enter leave]} (muuntaja/format-interceptor muuntaja/default-options)
        app (fn [request] (-> (->Context request) enter (handle +handler+) leave :response))
        request! (request-stream +json-request+)]

    (title "muuntaja: Interceptor JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 7.5µs
  (let [{:keys [enter leave]} (muuntaja/format-interceptor muuntaja/default-options)
        app (fn [request] (-> (->Context request) enter (handle +handler+) leave :response))
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: Interceptor JSON-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (slurp (:body (app (request!))))))
    (cc/quick-bench (app (request!)))))

;;
;; Run
;;

(defn all []
  (old)
  (content-type)
  (accept)
  (request)
  (parse-json)
  (ring-middleware-format-e2e)
  (ring-json-e2e)
  (muuntaja-e2e)
  (interceptor-e2e))

(comment
  (old)
  (content-type)
  (accept)
  (request)
  (parse-json)
  (ring-middleware-format-e2e)
  (ring-json-e2e)
  (muuntaja-e2e)
  (interceptor-e2e)
  (all))
