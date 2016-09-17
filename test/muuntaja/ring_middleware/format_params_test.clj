(ns muuntaja.ring-middleware.format-params-test
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [clojure.walk :refer [stringify-keys keywordize-keys]]
            [msgpack.core :as msgpack]
            [clojure.string :as string]
            [muuntaja.core :as muuntaja]
            [muuntaja.middleware :as middleware])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(defn wrap-api-params
  ([handler]
   (wrap-api-params handler muuntaja/default-options))
  ([handler opts]
   (-> handler
       (middleware/wrap-format
         (-> opts muuntaja/no-encoding)))))

(defn key-fn [s]
  (-> s (string/replace #"_" "-") keyword))

(deftest json-options-test
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-api-params
                          identity
                          (-> muuntaja/default-options
                              (muuntaja/with-formats [:json])
                              (muuntaja/with-decoder-opts :json {:keywords? key-fn})))
                         {:headers {"content-type" "application/json"}
                          :body (stream "{\"foo_bar\":\"bar\"}")}))))
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-api-params
                          identity
                          (-> muuntaja/default-options
                              (muuntaja/with-decoder-opts :json {:keywords? key-fn})))
                         {:headers {"content-type" "application/json"}
                          :body (stream "{\"foo_bar\":\"bar\"}")})))))

(defn yaml-echo [opts]
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:yaml])
            (muuntaja/with-decoder-opts :yaml opts)))))

(deftest augments-with-yaml-content-type
  (let [req {:headers {"content-type" "application/x-yaml; charset=UTF-8"}
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp ((yaml-echo {:keywords false}) req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def yaml-kw-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:yaml])
            (muuntaja/with-decoder-opts :yaml {:keywords true})))))

(deftest augments-with-yaml-kw-content-type
  (let [req {:headers {"content-type" "application/x-yaml; charset=UTF-8"}
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp (yaml-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def msgpack-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:msgpack])))))

(deftest augments-with-msgpack-content-type
  (let [req {:headers {"content-type" "application/msgpack"}
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def msgpack-kw-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:msgpack])
            (muuntaja/with-decoder-opts :msgpack {:keywords? true})))))

(deftest augments-with-msgpack-kw-content-type
  (let [req {:headers {"content-type" "application/msgpack"}
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def clojure-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:edn])))))

(deftest augments-with-clojure-content-type
  (let [req {:headers {"content-type" "application/clojure; charset=UTF-8"}
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(deftest augments-with-clojure-content-prohibit-eval-in-reader
  (let [req {:headers {"content-type" "application/clojure; charset=UTF-8"}
             :body (stream "{:foo #=(java.util.Date.)}")
             :params {"id" 3}}]
    (try
      (clojure-echo req)
      (is false "Eval in reader permits arbitrary code execution.")
      (catch Exception _
        (is true)))))

(deftest no-body-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3} (:params resp)))
    (is (= nil (:body-params resp)))))

(deftest whitespace-body-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "\t  ")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3} (:params resp)))
    (is (= nil (:body-params resp)))))

;; Transit

(defn stream-transit
  [fmt data]
  (let [out (ByteArrayOutputStream.)
        wrt (transit/writer out fmt)]
    (transit/write wrt data)
    (io/input-stream (.toByteArray out))))

(def transit-json-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:transit-json])))))

(deftest augments-with-transit-json-content-type
  (let [req {:headers {"content-type" "application/transit+json"}
             :body (stream-transit :json {:foo "bar"})
             :params {"id" 3}}
        resp (transit-json-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def transit-msgpack-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:transit-msgpack])))))

(deftest augments-with-transit-msgpack-content-type
  (let [req {:headers {"content-type" "application/transit+msgpack"}
             :body (stream-transit :msgpack {:foo "bar"})
             :params {"id" 3}}
        resp (transit-msgpack-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

;; HTTP Params

#_(comment
    (def api-echo
      (wrap-api-params identity))

    (def safe-api-echo
      (wrap-api-params identity
                       {:handle-error (fn [_ _ _] {:status 500})}))

    (deftest test-api-params-wrapper
      (let [req {:content-type "application/clojure; charset=UTF-8"
                 :body (stream "{:foo \"bar\"}")
                 :params {"id" 3}}
            resp (api-echo req)]
        (is (= {"id" 3 :foo "bar"} (:params resp)))
        (is (= {:foo "bar"} (:body-params resp)))
        (is (= 500 (get (safe-api-echo (assoc req :body (stream "{:foo \"bar}"))) :status)))))

    (defn stream-iso [s]
      (ByteArrayInputStream. (.getBytes s "ISO-8859-1")))

    (deftest test-different-params-charset
      (testing "with fixed charset"
        (let [req {:content-type "application/clojure; charset=ISO-8859-1"
                   :body (stream-iso "{:fée \"böz\"}")
                   :params {"id" 3}}
              app (wrap-api-params identity)
              resp (app req)]
          (is (not= {"id" 3 :fée "böz"} (:params resp)))
          (is (not= {:fée "böz"} (:body-params resp)))))
      (testing "with fixed charset"
        (let [req {:content-type "application/clojure; charset=ISO-8859-1"
                   :body (stream-iso "{:fée \"böz\"}")
                   :params {"id" 3}}
              app (wrap-api-params identity {:charset resolve-request-charset})
              resp (app req)]
          (is (= {"id" 3 :fée "böz"} (:params resp)))
          (is (= {:fée "böz"} (:body-params resp)))))))

(deftest test-list-body-request
  (let [req {:headers {"content-type" "application/json"}
             :body (ByteArrayInputStream.
                     (.getBytes "[\"gregor\", \"samsa\"]"))}]
    ((wrap-api-params
       (fn [{:keys [body-params]}] (is (= ["gregor" "samsa"] body-params))))
      req)))

(deftest test-optional-body
  ((wrap-api-params
     (fn [request]
       (is (nil? (:body request)))))
    {:body nil}))

(deftest test-custom-handle-error
  (are [format content-type body]
    (let [req {:body body
               :headers {"content-type" content-type}}
          resp ((-> identity
                    (wrap-api-params
                      (-> muuntaja/default-options
                          (assoc :formats [format])))
                    (middleware/wrap-exception (constantly {:status 999})))
                 req)]
      (= 999 (:status resp)))
    :json "application/json" "{:a 1}"
    :edn "application/edn" "{\"a\": 1}"))

;; Transit options

(defrecord Point [x y])

(def readers
  {"Point" (transit/read-handler (fn [[x y]] (Point. x y)))})

(def custom-transit-json-echo
  (-> identity
      (middleware/wrap-params)
      (wrap-api-params
        (-> muuntaja/default-options
            (muuntaja/with-formats [:transit-json])
            (muuntaja/with-decoder-opts :transit-json {:handlers readers})))))

(def transit-body "[\"^ \", \"~:p\", [\"~#Point\",[1,2]]]")

(deftest read-custom-transit
  (testing "wrap-api-params, transit options"
    (let [req (custom-transit-json-echo {:headers {"content-type" "application/transit+json"}
                                         :body (stream transit-body)})]
      (is (= {:p (Point. 1 2)}
             (:params req)
             (:body-params req))))))
