(ns ring-format.perf-test
  (:require [criterium.core :as cc]
            [ring-format.core :as rfc]
            [cheshire.core :as json]
            [ring.middleware.format-params :as rmfp]
            [ring.middleware.format :as rmf])
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

(defn request-stream
  ([request]
   (request-stream request 100000))
  ([request count]
   (let [i (atom 0)
         data (mapv
                (fn [_]
                  (->
                    request
                    (update :body #(ByteArrayInputStream. (.getBytes ^String %)))))
                (range count))]
     (fn []
       (let [item (nth data @i)]
         (swap! i inc)
         item)))))

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(def +json-request+
  {:headers {"content-type" "application/json"
             "accept" "application/json"}
   :body "{\"kikka\": 42}"})

(def +json-response+
  {:status 200
   :body {:kukka 24}})

(def +transit-json-request+
  {:headers {"content-type" "application/transit+json"
             "accept" "application/transit+json"}
   :body "[\"^ \",\"~:kikka\",42]"})

(def +handler+ (fn [request] {:status 200 :body (:body-params request)}))

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
    (title "R-M-F: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 2520ns
    (title "R-M-F: TRANSIT")
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
  (let [formats (-> rfc/default-options rfc/no-decoding rfc/no-encoding rfc/compile )]

    ; 52ns
    ; 38ns consumes & produces (-27%)
    ; 27ns compile (-29%) (-48%)
    (title "Content-type: JSON")
    (assert (= :json (rfc/extract-content-type-format formats +json-request+)))
    (cc/quick-bench
      (rfc/extract-content-type-format formats +json-request+))

    ; 65ns
    ; 55ns consumes & produces (-15%)
    ; 42ns compile (-24%) (-35%)
    (title "Content-type: TRANSIT")
    (assert (= :transit-json (rfc/extract-content-type-format formats +transit-json-request+)))
    (cc/quick-bench
      (rfc/extract-content-type-format formats +transit-json-request+))))

(defn accept []
  (let [formats (-> rfc/default-options rfc/no-decoding rfc/no-encoding rfc/compile )]

    ; 71ns
    ; 58ns consumes & produces (-18%)
    ; 48ns compile (-17%) (-32%)
    (title "Accept: TRANSIT")
    (assert (= :transit-json (rfc/extract-accept-format formats +transit-json-request+)))
    (cc/quick-bench
      (rfc/extract-accept-format formats +transit-json-request+))))

(defn request []
  (let [formats (-> rfc/default-options rfc/no-decoding rfc/no-encoding rfc/compile )]

    ; 179ns
    ; 187ns (records)
    (title "Accept & Contnet-type: JSON")
    (cc/quick-bench
      (rfc/format-request formats +json-request+))

    ; 211ns
    ; 226ns (records)
    (title "Accept & Contnet-type: Transit")
    (cc/quick-bench
      (rfc/format-request formats +transit-json-request+))))

(defn decode-encode []
  (let [formats (rfc/compile rfc/default-options)]

    ; 1131ns
    (title "Request - decode: JSON")
    (assert (= {:kikka 42} (:body (rfc/format-request formats +json-request+))))
    (cc/quick-bench
      (rfc/format-request formats +json-request+))

    ; 1204ns
    (title "Response - encode: JSON")
    (assert (= "{\"kukka\":24}" (:body (rfc/format-response formats +json-request+ +json-response+))))
    (cc/quick-bench
      (rfc/format-response formats +json-request+ +json-response+))

    ; 2406ns
    (title "Request & Response - encode: JSON")
    (let [handle-format (fn [request response]
                          (as-> request $
                                (rfc/format-request formats $)
                                (rfc/format-response formats $ response)))]
      (assert (= "{\"kukka\":24}" (:body (handle-format +json-request+ +json-response+))))
      (cc/quick-bench
        (handle-format +json-request+ +json-response+)))))

;;
;; handlers
;;

(defn ring-middleware-format []

  ; 10.2µs
  (let [app (rmfp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        next (request-stream +json-request+ 1000000)
        call #(app (next))]

    (title "RMF: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (call))))
    (cc/quick-bench (call)))

  ; 8.5µs
  (let [app (rmfp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        next (request-stream +transit-json-request+ 1000000)
        call #(app (next))]

    (title "RMF: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (call))))
    (cc/quick-bench (call)))

  ; 22.5µs
  (let [app (rmf/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        next (request-stream +json-request+ 1000000)
        call #(app (next))]

    (title "RMF: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (slurp (:body (call)))))
    (cc/quick-bench (call)))

  ; 21.0µs
  (let [app (rmf/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        next (request-stream +transit-json-request+ 1000000)
        call #(app (next))]

    (title "RMF: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (slurp (:body (call)))))
    (cc/quick-bench (call))))

;;
;; Run
;;

(defn all []
  (old)
  (content-type)
  (accept)
  (request)
  (decode-encode))

(comment
  (old)
  (content-type)
  (accept)
  (request)
  (decode-encode)
  (ring-middleware-format)
  (all))

