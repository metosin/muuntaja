(ns muuntaja.core-perf-test
  (:require [criterium.core :as cc]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.interceptor :as interceptor]
            [muuntaja.test_utils :refer :all]
            [cheshire.core :as cheshire]
            [ring.middleware.format-params]
            [ring.middleware.format]
            [ring.middleware.transit]
            [ring.middleware.json]
            [io.pedestal.http]
            [io.pedestal.http.body-params]
            [muuntaja.json :as json]
            [muuntaja.format.json :as json-format]
            [muuntaja.format.transit :as transit]
            [ring.core.protocols :as protocols]
            [clojure.java.io :as io])
  (:import (java.io InputStreamReader ByteArrayOutputStream ByteArrayInputStream File)))

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
  {:headers {"content-type" "application/transit+json; charset=utf-8"
             "accept" "application/transit+json"
             "accept-charset" "utf-8"}
   :body "[\"^ \",\"~:kikka\",42]"})

(defn- to-byte-stream [^String x ^String charset]
  (ByteArrayInputStream. (.getBytes x charset)))

(defrecord Hello [^String name]
  json-format/EncodeJson
  (encode-json [_ charset]
    (to-byte-stream (json/to-json {"hello" name}) charset)))

(def +handler+ (fn [request] {:status 200 :body (:body-params request)}))
(def +handler2+ (fn [_] {:status 200 :body (->Hello "yello")}))

(defn handle [context handler]
  (assoc context :response (handler (:request context))))

(defn ring-stream! [response]
  (let [os (ByteArrayOutputStream. 16384)]
    (protocols/write-body-to-stream (:body response) response os)
    os))

(defn fn-stream! [response]
  (let [os (ByteArrayOutputStream. 16384)]
    ((:body response) os)
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
    (cc/bench
      (api-request? +json-request+))

    ;; 2800ns
    (title "ring-middleware-format: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/bench
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
    (cc/bench
      (api-request? +json-request+))

    ;; 684ns
    (title "NAIVE: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/bench
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
    (cc/bench (m/negotiate-request m +json-request+))

    ; 65ns
    ; 55ns consumes & produces (-15%)
    ; 42ns compile (-24%) (-35%)
    ; 43ns + charset, memoized
    (title "Content-type: TRANSIT")
    (assert (= ["application/transit+json" "utf-16"] (m/negotiate-request m +transit-json-request+)))
    (cc/bench (m/negotiate-request m +transit-json-request+))))

(defn accept []
  (let [m (m/create m/default-options)]

    ; 71ns
    ; 58ns consumes & produces (-18%)
    ; 48ns compile (-17%) (-32%)
    ; 94ns + charset, memoized
    (title "Accept: TRANSIT")
    (assert (= ["application/transit+json" "utf-16"] (m/negotiate-response m +transit-json-request+)))
    (cc/bench (m/negotiate-response m +transit-json-request+))))

(defn negotiate-request []
  (let [m (-> m/default-options
              (assoc :decode-request-body? false)
              (m/create))]

    ; 179ns
    ; 187ns (records)
    ; 278ns (+charset)
    (title "Negotiate Request: JSON")
    (cc/bench
      (m/format-request m +json-request+))

    ; 211ns
    ; 226ns (records)
    ; 278ns (+charset)
    (title "Negotiate Request: Transit")
    (cc/bench
      (m/format-request m +transit-json-request+))))

(defn identity-encode-decode []
  (let [m (-> m/default-options
              (assoc-in [:formats "application/json" :encoder] (fn [x _] x))
              (assoc-in [:formats "application/json" :decoder] (fn [x _] x))
              (m/create))]

    ; 143ns
    (title "naive - JSON identity")
    (let [wrap (fn [request]
                 (-> request
                     (assoc :body-params (-> request :body identity identity))
                     (assoc :body nil)))]
      (cc/bench
        (wrap +json-request+)))

    ; 474ns
    ; 626ns (+charset)
    ; 540ns (content-type)
    (title "request-format - JSON identity")
    (cc/bench
      (m/format-request m +json-request+))

    ; 670ns
    ; 873ns (+charset)
    ; 706ns (content-type)
    (title "requset-format & response-format - JSON identity")
    (let [wrap (fn [request]
                 (let [req (m/format-request m request)]
                   (->> (+handler+ req) (m/format-response m req))))]
      (cc/bench
        (wrap +json-request+)))))

(defn parse-json []

  ; 2.9µs
  ; 2.5µs (no dynamic binding if not needed)
  ; 1.8µs ???
  (title "muuntaja: parse-json-stream")
  (let [parse (m/decoder (m/create m/default-options) "application/json")
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (:body (request!)))))
    (cc/bench (parse (:body (request!)))))

  ; 2.5µs
  ; 1.8µs ???
  (title "cheshire: parse-json-stream")
  (let [parse #(cheshire/parse-stream (InputStreamReader. %) true)
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (:body (request!)))))
    (cc/bench (parse (:body (request!)))))

  ; 5.1µs
  ; 3.9µs ???
  (title "cheshire: parse-json-string")
  (let [parse #(cheshire/parse-string % true)
        request! (request-stream +json-request+)]
    (assert (= {:kikka 42} (parse (slurp (:body (request!))))))
    (cc/bench (parse (slurp (:body (request!)))))))

(defn ring-middleware-format-e2e []

  ; 8.5µs
  (let [app (ring.middleware.format-params/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/bench (app (request!))))

  ; 7.8µs
  (let [app (ring.middleware.format-params/wrap-restful-params +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/bench (app (request!))))

  ; 14.4µs && 17.4µs
  (let [app (ring.middleware.format/wrap-restful-format +handler+ {:charset "utf-8" :formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST-RESPONSE - fixed charset")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 22.3µs && 24.7µs
  (let [app (ring.middleware.format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +json-request+)]

    (title "ring-middleware-format: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 22.5µs && 25.5µs
  (let [app (ring.middleware.format/wrap-restful-format +handler+ {:charset "utf-8" :formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST-RESPONSE - fixed charset")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 24.7µs && 27.4µs
  (let [app (ring.middleware.format/wrap-restful-format +handler+ {:formats [:json-kw :edn :msgpack-kw :yaml-kw :transit-msgpack :transit-json]})
        request! (request-stream +transit-json-request+)]

    (title "ring-middleware-format: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!))))))

(defn ring-json-e2e []
  (let [+handler+ (fn [request] {:status 200 :body (:body request)})]

    ; 5.2µs
    (let [app (ring.middleware.json/wrap-json-body +handler+ {:keywords? true})
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST")
      (assert (= {:kikka 42} (:body (app (request!)))))
      (cc/bench (app (request!))))

    ; 7.9µs && 16.2µs
    (let [app (-> +handler+
                  (ring.middleware.json/wrap-json-body {:keywords? true})
                  (ring.middleware.json/wrap-json-response))
          request! (request-stream +json-request+)]

      (title "ring-json: JSON-REQUEST-RESPONSE")
      (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
      (cc/bench (app (request!)))
      (cc/bench (ring-stream! (app (request!)))))))

(defn muuntaja-e2e []

  ; 2.3µs
  ; 2.6µs (negotions)
  ; 2.6µs (content-type)
  (let [app (middleware/wrap-format +handler+ (m/transform-formats
                                                m/default-options
                                                #(dissoc %2 :encoder :encode-protocol)))
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/bench (app (request!))))

  ; 3.6µs
  ; 4.2µs (negotions)
  ; 4.1µs (content-type)
  (let [app (middleware/wrap-format +handler+ (m/transform-formats
                                                m/default-options
                                                #(dissoc %2 :encoder)))
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST")
    (assert (= {:kikka 42} (:body (app (request!)))))
    (cc/bench (app (request!))))

  ; 3.6µs
  ; 4.4µs (negotions)
  ; 4.2µs (content-type)
  ; 4.2µs && 7.0µs
  (let [app (middleware/wrap-format +handler+ m/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 7.1µs
  ; 8.8µs (negotions)
  ; 8.2µs (content-type)
  ; 9.4µs && 11.9µs
  (let [app (middleware/wrap-format +handler+ m/default-options)
        request! (request-stream +transit-json-request+)]

    (title "muuntaja: TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 3.8µs
  ; 2.6µs Protocol (-30%)
  ; 3.3µs (negotions)
  ; 3.2µs (content-type)
  ; 3.3µs && 5.7µs
  (let [app (middleware/wrap-format +handler2+ m/default-options)
        request! (request-stream +json-request+)]

    (title "muuntaja: JSON-REQUEST-RESPONSE (PROTOCOL)")
    (assert (= "{\"hello\":\"yello\"}" (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!))))))

(defn pedestal-interceptor-e2e []

  ; 3.8µs && 7.2µs
  (let [enter (:enter (io.pedestal.http.body-params/body-params))
        handler (fn [ctx] (assoc ctx :response {:status 200 :body (-> ctx :request :json-params)}))
        leave (:leave io.pedestal.http/json-body)
        app (fn [ctx] (-> ctx enter handler leave :response))
        request! (context-stream (assoc +json-request+ :content-type "application/json"))]

    (title "pedestal: Interceptor JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (fn-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (fn-stream! (app (request!)))))

  ; 5.6µs && 11.9µs
  (let [enter (:enter (io.pedestal.http.body-params/body-params))
        handler (fn [ctx] (assoc ctx :response {:status 200 :body (-> ctx :request :transit-params)}))
        leave (:leave io.pedestal.http/transit-body)
        app (fn [ctx] (-> ctx enter handler leave :response))
        request! (context-stream (assoc +transit-json-request+ :content-type "application/transit+json"))]

    (title "pedestal: Interceptor TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (fn-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (fn-stream! (app (request!))))))

(defn interceptor-e2e []

  ; 3.8µs
  ; 4.7µs (negotiations)
  ; 4.6µs (content-type)
  ; 4.4µs && 7.1µs
  ; 3.5µs && 6.7µs (streaming)
  (let [{:keys [enter leave]} (interceptor/format-interceptor m/default-options)
        app (fn [ctx] (-> ctx enter (handle +handler+) leave :response))
        request! (context-stream +json-request+)]

    (title "muuntaja: Interceptor JSON-REQUEST-RESPONSE")
    (assert (= (:body +json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!)))))

  ; 7.5µs
  ; 8.7µs (negotiations)
  ; 8.5µs (content-type)
  ; 5.5µs && 12.2µs (streaming)
  (let [{:keys [enter leave]} (interceptor/format-interceptor m/default-options)
        app (fn [ctx] (-> ctx enter (handle +handler+) leave :response))
        request! (context-stream +transit-json-request+)]

    (title "muuntaja: Interceptor TRANSIT-REQUEST-RESPONSE")
    (assert (= (:body +transit-json-request+) (str (ring-stream! (app (request!))))))
    (cc/bench (app (request!)))
    (cc/bench (ring-stream! (app (request!))))))

;;
;; PERF
;;

(defn next-number []
  (as-> (io/file "perf") $
        (.listFiles $)
        (map #(.getName ^File %) $)
        (keep (fn [s]
                (Long/parseLong (re-find #"\d+" s))) $)
        (apply max (or (seq $) [0]))
        (inc $)))

(defn mean [result]
  (if (seq result)
    (-> result :mean first (* 1000000000) long)))

(defn save-results! [file results]
  (spit file (with-out-str (clojure.pprint/pprint results)))
  results)

(defn polished [result]
  (cond-> (dissoc result :results)
          (:outliers result) (update :outliers (partial into {}))))

(defmacro bench [& body]
  `(let [result# (cc/quick-benchmark ~@body {})]
     (cc/report-result result#)
     result#))

(defmacro report-bench [acc type size tool & body]
  `(do
     (title (str ~type " (" ~size ") - " ~tool))
     (swap! ~acc assoc-in [~type ~size ~tool] (polished (bench ~@body)))))

;; file sizes about about the size. good enough.
(defn e2e-json-comparison-different-payloads []
  (let [results (atom {})]
    (doseq [size ["10b" "100b" "1k" "10k" "100k"]
            :let [file (str "dev-resources/json" size ".json")
                  data (cheshire/parse-string (slurp file))
                  request (json-request data)
                  request! (request-stream request)]]

      (title file)

      ;   22µs (10b)
      ;   40µs (100b)
      ;  269µs (1k)
      ; 2200µs (10k)
      ; 4730µs (100k)
      (let [app (-> +handler+ (ring.middleware.format/wrap-restful-format))]
        (report-bench results :json size "r-m-f (defaults)" (ring-stream! (app (request!)))))

      ;   19µs (10b)
      ;   24µs (100b)
      ;   39µs (1k)
      ;  260µs (10k)
      ; 2340µs (100k)
      (let [app (-> +handler+ (ring.middleware.format/wrap-restful-format {:formats [:json-kw :edn :msgpack :yaml :transit-msgpack :transit-json]
                                                                           :charset ring.middleware.format-params/get-or-default-charset}))]
        (report-bench results :json size "r-m-f (tuned)" (ring-stream! (app (request!)))))

      ;   15µs (10b)
      ;   18µs (100b)
      ;   35µs (1k)
      ;  270µs (10k)
      ; 2500µs (100k)
      (let [+handler+ (fn [request] {:status 200 :body (:body request)})
            app (-> +handler+
                    (ring.middleware.json/wrap-json-body)
                    (ring.middleware.json/wrap-json-response))]
        (report-bench results :json size "ring-json" (ring-stream! (app (request!)))))

      ;    7µs (10b)
      ;    9µs (100b)
      ;   23µs (1k)
      ;  215µs (10k)
      ; 2100µs (100k)
      (let [app (-> +handler+ (middleware/wrap-format))]
        (report-bench results :json size "muuntaja" (ring-stream! (app (request!)))))

      ;    4µs (10b)
      ;    6µs (100b)
      ;   16µs (1k)
      ;  153µs (10k)
      ; 1500µs (100k)
      (let [app (-> +handler+ (middleware/wrap-format
                                (assoc-in
                                  m/default-options
                                  [:formats "application/json"]
                                  {:encoder [json-format/make-muuntaja-json-encoder {:keywords? true}]
                                   :decoder [json-format/make-muuntaja-json-decoder]})))]
          (report-bench results :json size "muuntaja (jackson)" (ring-stream! (app (request!))))))

    (save-results! (format "perf/json-results%s.edn" (next-number)) @results)))

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

    ;   26µs (10b)
    ;   34µs (100b)
    ;   52µs (1k)
    ;  240µs (10k)
    ; 2000µs (100k)
    (title "ring-middleware-format: TRANSIT-JSON-REQUEST-RESPONSE (defaults)")
    (let [app (-> +handler+ (ring.middleware.format/wrap-restful-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))

    ;   25µs (10b)
    ;   32µs (100b)
    ;   46µs (1k)
    ;  240µs (10k)
    ; 2000µs (100k)
    (title "ring-middleware-format: TRANSIT-JSON-REQUEST-RESPONSE (tuned)")
    (let [app (-> +handler+ (ring.middleware.format/wrap-restful-format {:formats [:json-kw :edn :msgpack :yaml :transit-msgpack :transit-json]
                                                                         :charset ring.middleware.format-params/get-or-default-charset}))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))

    ;   21µs (10b)
    ;   26µs (100b)
    ;   44µs (1k)
    ;  258µs (10k)
    ; 2390µs (100k)
    (title "ring-transit: TRANSIT-JSON-REQUEST-RESPONSE")
    (let [+handler+ (fn [request] {:status 200 :body (:body request)})
          app (-> +handler+
                  (ring.middleware.transit/wrap-transit-body)
                  (ring.middleware.transit/wrap-transit-response))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))

    ;   12µs (10b)
    ;   18µs (100b)
    ;   31µs (1k)
    ;  220µs (10k)
    ; 1900µs (100k)
    (title "muuntaja: TRANSIT-JSON-REQUEST-RESPONSE")
    (let [app (-> +handler+ (middleware/wrap-format (assoc-in m/default-options [:formats "application/transit+json" :encoder] [(partial transit/make-transit-encoder :json)])))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))

    ;   11µs (10b)
    ;   17µs (100b)
    ;   30µs (1k)
    ;  215µs (10k)
    ; 1900µs (100k)
    (title "muuntaja: TRANSIT-JSON-REQUEST-RESPONSE, streaming")
    (let [app (-> +handler+ (middleware/wrap-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))))

(defrecord Json10b [^Long imu]
  json-format/EncodeJson
  (encode-json [_ charset]
    (to-byte-stream (json/to-json {:imu imu}) charset)))

(defn e2e-muuntaja-json []
  (let [data (->Json10b 42)
        handler (fn [_] {:status 200 :body data})
        request (json-request data)
        request! (request-stream request)]

    ; 5.4µs (10b)
    (title "muuntaja: JSON-REQUEST-RESPONSE (hand-crafted)z")
    (let [app (-> handler (middleware/wrap-format))]
      #_(println (str (ring-stream! (app (request!)))))
      (cc/bench (ring-stream! (app (request!)))))))

(defn request-streams []

  ;; 28ns
  (let [r (request-stream +json-request+)]
    (title "request-stream (baseline)")
    (cc/bench
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
  (pedestal-interceptor-e2e)
  (interceptor-e2e)
  (request-streams)
  (e2e-json-comparison-different-payloads)
  (e2e-transit-comparison-different-payloads)
  (e2e-muuntaja-json)
  (all))

(comment
  (doseq [[type results] (read-string (slurp (format "perf/json-results%s.edn" (dec (next-number)))))
          [size data] results
          :let [min-value (->> data vals (keep mean) (apply min))
                _ (printf "\n\u001B[35m%s (%s)\u001B[0m\n\n" type size)]
          [k v] data
          :let [m (mean v)]]
    (printf "\t%20s\t%10sns %5s\n" k m (-> (/ m min-value) (- 1) (* 100) int))))
