(ns muuntaja.core-perf-test
  (:require [criterium.core :as cc]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.interceptor :as interceptor]
            [muuntaja.json :as json]
            [muuntaja.test_utils :refer :all]
            [cheshire.core :as cheshire]
            [ring.middleware.format-params :as ring-middleware-formatp]
            [ring.middleware.format :as ring-middleware-format]
            [ring.middleware.json :as rmj]
            [muuntaja.formats :as formats]
            [ring.core.protocols :as protocols])
  (:import (java.io InputStreamReader ByteArrayOutputStream ByteArrayInputStream)))

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

(defn json-request [data]
  {:headers {"content-type" "application/json"
             "accept" "application/json"}
   :body (cheshire/generate-string data)})

(def +json-request+ (json-request {:kikka 42}))

(defn transit-request [data]
  {:headers {"content-type" "application/transit+json"
             "accept" "application/transit+json"}
   :body (slurp (m/encode (m/create) "application/transit+json" data))})

(def +transit-json-request+
  {:headers {"content-type" "application/transit+json; charset=utf-16"
             "accept" "application/transit+json"
             "accept-charset" "utf-16"}
   :body "[\"^ \",\"~:kikka\",42]"})

(defn byte-stream [x]
  (ByteArrayInputStream. (.getBytes (str x))))

(defrecord Hello [^String name]
  formats/EncodeJson
  (encode-json [_]
    (byte-stream
      (str (doto (json/object)
             (.put "hello" name))))))

(def +handler+ (fn [request] {:status 200 :body (:body-params request)}))
(def +handler2+ (fn [_] {:status 200 :body (->Hello "yello")}))

(defn handle [context handler]
  (assoc context :response (handler (:request context))))

(defn ring-stream! [response]
  (let [os (ByteArrayOutputStream. 16384)]
    (protocols/write-body-to-stream (:body response) response os)
    os))

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

    ;; 605ns
    (title "ring-middleware-format: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 2800ns
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
                       (match? "application/transit+json; charset=utf-16")
                       (match? "application/transi+msgpack"))]

    ;; 101ns
    (title "NAIVE: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 684ns
    (title "NAIVE: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+))))

;;
;; Real
;;

(defn content-type []
  (let [m (m/create m/default-options)]

    ; 52ns
    ; 38ns consumes & produces (-27%)
    ; 27ns compile (-29%) (-48%)
    ; 49ns + charset, memoized
    (title "Content-type: JSON")
    (assert (= ["application/json" "utf-8"] (m/negotiate-request m +json-request+)))
    (cc/quick-bench (m/negotiate-request m +json-request+))

    ; 65ns
    ; 55ns consumes & produces (-15%)
    ; 42ns compile (-24%) (-35%)
    ; 43ns + charset, memoized
    (title "Content-type: TRANSIT")
    (assert (= ["application/transit+json" "utf-16"] (m/negotiate-request m +transit-json-request+)))
    (cc/quick-bench (m/negotiate-request m +transit-json-request+))))

(defn accept []
  (let [m (m/create m/default-options)]

    ; 71ns
    ; 58ns consumes & produces (-18%)
    ; 48ns compile (-17%) (-32%)
    ; 94ns + charset, memoized
    (title "Accept: TRANSIT")
    (assert (= ["application/transit+json" "utf-16"] (m/negotiate-response m +transit-json-request+)))
    (cc/quick-bench (m/negotiate-response m +transit-json-request+))))

(defn negotiate-request []
  (let [formats (-> m/default-options
                    (assoc :decode? false)
                    (m/create))]

    ; 179ns
    ; 187ns (records)
    ; 278ns (+charset)
    (title "Negotiate Request: JSON")
    (cc/quick-bench
      (m/format-request formats +json-request+))

    ; 211ns
    ; 226ns (records)
    ; 278ns (+charset)
    (title "Negotiate Request: Transit")
    (cc/quick-bench
      (m/format-request formats +transit-json-request+))))

(defn identity-encode-decode []
  (let [formats (-> m/default-options
                    (assoc-in [:formats "application/json" :encoder] (fn [x _] x))
                    (assoc-in [:formats "application/json" :decoder] (fn [x _] x))
                    (m/create))]

    ; 143ns
    (title "naive - JSON identity")
    (let [wrap (fn [request]
                 (-> request
                     (assoc :body-params (-> request :body identity identity))
                     (assoc :body nil)))]
      (cc/quick-bench
        (wrap +json-request+)))

    ; 474ns
    ; 626ns (+charset)
    ; 540ns (content-type)
    (title "request-format - JSON identity")
    (cc/quick-bench
      (m/format-request formats +json-request+))

    ; 670ns
    ; 873ns (+charset)
    ; 706ns (content-type)
    (title "requset-format & response-format - JSON identity")
    (let [wrap (fn [request]
                 (let [req (m/format-request formats request)]
                   (->> (+handler+ req) (m/format-response formats req))))]
      (cc/quick-bench
        (wrap +json-request+)))))

(defn parse-json []

  ; 2.9µs
  ; 2.5µs (no dynamic binding if not needed)
  ; 1.8µs ???
  (title "muuntaja: parse-json-stream")
  (let [parse (m/decoder (m/create m/default-options) "application/json")
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (:body (request!)))))
    (cc/quick-bench (parse (:body (request!)))))

  ; 2.5µs
  ; 1.8µs ???
  (title "cheshire: parse-json-stream")
  (let [parse #(cheshire/parse-stream (InputStreamReader. %) true)
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (:body (request!)))))
    (cc/quick-bench (parse (:body (request!)))))

  ; 5.1µs
  ; 3.9µs ???
  (title "cheshire: parse-json-string")
  (let [parse #(cheshire/parse-string % true)
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (slurp (:body (request!))))))
    (cc/quick-bench (parse (slurp (:body (request!)))))))

(defn ring-middleware-format-e2e []

  ; 8.5µs
  (let [app (ring-middleware-formatp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 7.8µs
  (let [app (ring-middleware-formatp/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 14.4µs && 17.4µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:charset "utf-8" :formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST-RESPONSE - fixed charset")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 22.3µs && 24.7µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 22.5µs && 25.5µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:charset "utf-8" :formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST-RESPONSE - fixed charset")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 24.7µs && 27.4µs
  (let [app (ring-middleware-format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!))))))

(defn ring-json-e2e []
  (let [+handler+ (fn [request] {:status 200 :body (:body request)})]

    ; 5.2µs
    (let [app (rmj/wrap-json-body +handler+ {:keywords? true})
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST")
      (assert (= {:kikka 42} (:body (app (request!)))))
      (cc/quick-bench (app (request!))))

    ; 7.9µs && 16.2µs
    (let [app (-> +handler+ (rmj/wrap-json-body {:keywords? true}) (rmj/wrap-json-response))
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST-RESPONSE")
      (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
      (cc/quick-bench (app (request!)))
      (cc/quick-bench (ring-stream! (app (request!)))))))

(defn muuntaja-e2e []

  ; 2.3µs
  ; 2.6µs (negotions)
  ; 2.6µs (content-type)
  (let [app (middleware/wrap-format +handler+ (-> m/default-options m/no-encoding))
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 3.6µs
  ; 4.2µs (negotions)
  ; 4.1µs (content-type)
  (let [app (middleware/wrap-format +handler+ (-> m/default-options m/no-encoding))
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/quick-bench (app (request!))))

  ; 3.6µs
  ; 4.4µs (negotions)
  ; 4.2µs (content-type)
  ; 4.2µs && 7.0µs
  (let [app (middleware/wrap-format +handler+ m/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 7.1µs
  ; 8.8µs (negotions)
  ; 8.2µs (content-type)
  ; 9.4µs && 11.9µs
  (let [app (middleware/wrap-format +handler+ m/default-options)
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 3.8µs
  ; 2.6µs Protocol (-30%)
  ; 3.3µs (negotions)
  ; 3.2µs (content-type)
  ; 3.3µs && 5.7µs
  (let [app (middleware/wrap-format +handler2+ m/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE (PROTOCOL)")
    (assert (= "{\"hello\":\"yello\"}" (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!))))))

(defn interceptor-e2e []

  ; 3.8µs
  ; 4.7µs (negotiations)
  ; 4.6µs (content-type)
  ; 4.4µs && 7.1µs
  (let [{:keys [enter leave]} (interceptor/format-interceptor m/default-options)
        app (fn [ctx] (-> ctx enter (handle +handler+) leave :response))
        request! (context-stream +json-request+)]

    (title "muuntaja: Interceptor JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!)))))

  ; 7.5µs
  ; 8.7µs (negotiations)
  ; 8.5µs (content-type)
  ; 10.4µs && 12.9µs
  (let [{:keys [enter leave]} (interceptor/format-interceptor m/default-options)
        app (fn [ctx] (-> ctx enter (handle +handler+) leave :response))
        request! (context-stream +transit-json-request+)]

    (title "muuntaja: Interceptor TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/quick-bench (app (request!)))
    (cc/quick-bench (ring-stream! (app (request!))))))

;; file sizes about about the size. good enough.
(defn e2e-json-comparison-different-payloads []
  (doseq [file ["dev-resources/json10b.json"
                "dev-resources/json100b.json"
                "dev-resources/json1k.json"
                "dev-resources/json10k.json"
                "dev-resources/json100k.json"]
          :let [data (cheshire/parse-string (slurp file))
                request (json-request data)
                request! (request-stream request)]]

    (title file)

    ;   22µs (10b)
    ;   41µs (100b)
    ;  306µs (1k)
    ; 2200µs (10k)
    ; 5000µs (100k)
    (title "ring-middleware-format: JSON-REQUEST-RESPONSE")
    (let [app (-> +handler+ (ring-middleware-format/wrap-restful-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))

    ;   15µs (10b)
    ;   18µs (100b)
    ;   36µs (1k)
    ;  280µs (10k)
    ; 2500µs (100k)
    (title "ring-json: JSON-REQUEST-RESPONSE")
    (let [+handler+ (fn [request] {:status 200 :body (:body request)})
          app (-> +handler+ (rmj/wrap-json-body) (rmj/wrap-json-response))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))

    ;    7µs (10b)
    ;    9µs (100b)
    ;   23µs (1k)
    ;  215µs (10k)
    ; 2100µs (100k)
    (title "muuntaja: JSON-REQUEST-RESPONSE")
    (let [app (-> +handler+ (middleware/wrap-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))

    ;    7µs (10b)
    ;    9µs (100b)
    ;   23µs (1k)
    ;  215µs (10k)
    ; 2100µs (100k)
    (title "muuntaja: JSON-REQUEST-RESPONSE, streaming")
    (let [app (-> +handler+ (middleware/wrap-format (assoc-in m/default-options [:formats "application/json" :encoder] [formats/make-streaming-json-encoder])))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))))

;; file sizes about about the size in JSON. Smaller with transit.
(defn e2e-transit-comparison-different-payloads []
  (doseq [file ["dev-resources/json10b.json"
                "dev-resources/json100b.json"
                "dev-resources/json1k.json"
                "dev-resources/json10k.json"
                "dev-resources/json100k.json"]
          :let [data (cheshire/parse-string (slurp file))
                request (transit-request data)
                request! (request-stream request)]]

    (title file)

    ;   24µs (10b)
    ;   34µs (100b)
    ;   46µs (1k)
    ;  240µs (10k)
    ; 2000µs (100k)
    (title "ring-middleware-format: TRANSIT-JSON-REQUEST-RESPONSE")
    (let [app (-> +handler+ (ring-middleware-format/wrap-restful-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))

    ;   12µs (10b)
    ;   18µs (100b)
    ;   31µs (1k)
    ;  220µs (10k)
    ; 1900µs (100k)
    (title "muuntaja: TRANSIT-JSON-REQUEST-RESPONSE")
    (let [app (-> +handler+ (middleware/wrap-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))

    ;   11µs (10b)
    ;   17µs (100b)
    ;   30µs (1k)
    ;  215µs (10k)
    ; 1900µs (100k)
    (title "muuntaja: TRANSIT-JSON-REQUEST-RESPONSE, streaming")
    (let [app (-> +handler+ (middleware/wrap-format (assoc-in m/default-options [:formats "application/transit+json" :encoder] [(partial formats/make-streaming-transit-encoder :json)])))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/quick-bench (ring-stream! (app (request!)))))))

(defn request-streams []

  ;; 28ns
  (let [r (request-stream +json-request+)]
    (title "request-stream (baseline)")
    (cc/quick-bench
      (r))))

;;
;; Run
;;

(comment
  (old)
  (content-type)
  (accept)
  (negotiate-request)
  (identity-encode-decode)
  (parse-json)
  (ring-middleware-format-e2e)
  (ring-json-e2e)
  (muuntaja-e2e)
  (interceptor-e2e)
  (request-streams)
  (e2e-json-comparison-different-payloads)
  (e2e-transit-comparison-different-payloads)
  (all))
