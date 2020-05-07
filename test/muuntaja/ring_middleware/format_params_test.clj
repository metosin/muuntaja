(ns muuntaja.ring-middleware.format-params-test
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [clojure.walk :refer [stringify-keys keywordize-keys]]
            [muuntaja.format.msgpack :as msgpack-format]
            [muuntaja.format.yaml :as yaml-format]
            [msgpack.core :as msgpack]
            [clojure.string :as string]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def default-options
  (-> m/default-options
      (m/install msgpack-format/format)
      (m/install yaml-format/format)
      (assoc-in [:formats "application/json" :matches] #"^application/(.+\+)?json$")
      (assoc-in [:formats "application/edn" :matches] #"^application/(vnd.+)?(x-)?(clojure|edn)$")
      (assoc-in [:formats "application/msgpack" :matches] #"^application/(vnd.+)?(x-)?msgpack$")
      (assoc-in [:formats "application/x-yaml" :matches] #"^(application|text)/(vnd.+)?(x-)?yaml$")
      (assoc-in [:formats "application/transit+json" :matches] #"^application/(vnd.+)?(x-)?transit\+json$")
      (assoc-in [:formats "application/transit+msgpack" :matches] #"^application/(vnd.+)?(x-)?transit\+msgpack$")
      (m/transform-formats
        #(dissoc %2 :encoder))))

(defn wrap-api-params
  ([handler]
   (wrap-api-params handler default-options))
  ([handler opts]
   (-> handler
       (middleware/wrap-params)
       (middleware/wrap-format opts))))

(defn key-fn [s]
  (-> s (string/replace #"_" "-") keyword))

(deftest json-options-test
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-api-params
                          identity
                          (-> default-options
                              (m/select-formats ["application/json"])
                              (assoc-in
                                [:formats "application/json" :decoder-opts]
                                {:decode-key-fn key-fn})))
                        {:headers {"content-type" "application/json"}
                         :body (stream "{\"foo_bar\":\"bar\"}")}))))
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-api-params
                          identity
                          (-> default-options
                              (assoc-in
                                [:formats "application/json" :decoder-opts]
                                {:decode-key-fn key-fn})))
                        {:headers {"content-type" "application/json"}
                         :body (stream "{\"foo_bar\":\"bar\"}")})))))

(defn yaml-echo [opts]
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/x-yaml"])
            (assoc-in
              [:formats "application/x-yaml" :decoder-opts]
              opts)))))

(deftest augments-with-yaml-content-type
  (let [req {:headers {"content-type" "application/x-yaml; charset=UTF-8"}
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp ((yaml-echo {:keywords false}) req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def yaml-kw-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/x-yaml"])
            (assoc-in
              [:formats "application/x-yaml" :decoder-opts]
              {:keywords true})))))

(deftest augments-with-yaml-kw-content-type
  (let [req {:headers {"content-type" "application/x-yaml; charset=UTF-8"}
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp (yaml-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def msgpack-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/msgpack"])
            (assoc-in
              [:formats "application/msgpack" :decoder-opts]
              {:keywords? false})))))

(deftest augments-with-msgpack-content-type
  (let [req {:headers {"content-type" "application/msgpack"}
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def msgpack-kw-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/msgpack"])))))

(deftest augments-with-msgpack-kw-content-type
  (let [req {:headers {"content-type" "application/msgpack"}
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def clojure-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/edn"])))))

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
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/transit+json"])))))

(deftest augments-with-transit-json-content-type
  (let [req {:headers {"content-type" "application/transit+json"}
             :body (stream-transit :json {:foo "bar"})
             :params {"id" 3}}
        resp (transit-json-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def transit-msgpack-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/transit+msgpack"])))))

(deftest augments-with-transit-msgpack-content-type
  (let [req {:headers {"content-type" "application/transit+msgpack"}
             :body (stream-transit :msgpack {:foo "bar"})
             :params {"id" 3}}
        resp (transit-msgpack-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

;; HTTP Params

(def api-echo
  (-> identity
      (wrap-api-params)))

(def safe-api-echo
  (-> identity
      (wrap-api-params)
      (middleware/wrap-exception (constantly {:status 500}))))

(deftest test-api-params-wrapper
  (let [req {:headers {"content-type" "application/clojure; charset=UTF-8"}
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
        resp (api-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))
    (is (= 500 (get (safe-api-echo (assoc req :body (stream "{:foo \"bar}"))) :status)))))

(defn stream-iso [s]
  (ByteArrayInputStream. (.getBytes s "ISO-8859-1")))

(deftest test-different-params-charset
  #_(testing "with fixed charset"
      (let [req {:headers {"content-type" "application/clojure; charset=ISO-8859-1"}
                 :body (stream-iso "{:fée \"böz\"}")
                 :params {"id" 3}}
            app (-> identity
                    (wrap-api-params))
            resp (app req)]
        (is (not= {"id" 3 :fée "böz"} (:params resp)))
        (is (not= {:fée "böz"} (:body-params resp)))))
  (testing "with fixed charset"
    (let [req {:headers {"content-type" "application/clojure; charset=ISO-8859-1"}
               :body (stream-iso "{:fée \"böz\"}")
               :params {"id" 3}}
          app (-> identity
                  (wrap-api-params
                    (assoc default-options :charsets m/available-charsets)))
          resp (app req)]
      (is (= {"id" 3 :fée "böz"} (:params resp)))
      (is (= {:fée "böz"} (:body-params resp))))))

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
                      (-> default-options
                          (m/select-formats [format])))
                    (middleware/wrap-exception (constantly {:status 999})))
                req)]
      (= 999 (:status resp)))
    "application/json" "application/json" "{:a 1}"
    "application/edn" "application/edn" "{\"a\": 1}"))

;; Transit options

(defrecord Point [x y])

(def readers
  {"Point" (transit/read-handler (fn [[x y]] (Point. x y)))})

(def custom-transit-json-echo
  (-> identity
      (wrap-api-params
        (-> default-options
            (m/select-formats ["application/transit+json"])
            (assoc-in
              [:formats "application/transit+json" :decoder-opts]
              {:handlers readers})))))

(def transit-body "[\"^ \", \"~:p\", [\"~#Point\",[1,2]]]")

(deftest read-custom-transit
  (testing "wrap-api-params, transit options"
    (let [req (custom-transit-json-echo {:headers {"content-type" "application/transit+json"}
                                         :body (stream transit-body)})]
      (is (= {:p (Point. 1 2)}
             (:params req)
             (:body-params req))))))
