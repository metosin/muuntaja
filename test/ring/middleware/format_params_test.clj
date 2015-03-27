(ns ring.middleware.format-params-test
  (:use [clojure.test]
        [ring.middleware.format-params])
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [cognitect.transit :as transit]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def json-echo
  (wrap-json-params identity))

;; stolen from ring-json-params to confirm compatibility

(deftest noop-with-other-content-type
  (let [req {:content-type "application/xml"
             :body (stream "<xml></xml>")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= "<xml></xml>" (slurp (:body resp))))
    (is (= {"id" 3} (:params resp)))
    (is (nil? (:json-params resp)))))

(deftest augments-with-json-content-type
  (let [req {:content-type "application/json; charset=UTF-8"
             :body (stream "{\"foo\": \"bar\"}")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(deftest augments-with-vnd-json-content-type
  (let [req {:content-type "application/vnd.foobar+json; charset=UTF-8"
             :body (stream "{\"foo\": \"bar\"}")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def yaml-echo
  (wrap-yaml-params identity))

(deftest augments-with-yaml-content-type
  (let [req {:content-type "application/x-yaml; charset=UTF-8"
             :body (stream "foo: bar")
             :params {"id" 3}}
             resp (yaml-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def clojure-echo
  (wrap-clojure-params identity))

(deftest augments-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
             resp (clojure-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))


(deftest augments-with-clojure-content-prohibit-eval-in-reader
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo #=(java.util.Date.)}")
             :params {"id" 3}}]
    (try
      (let [resp (clojure-echo req)]
        (is false "Eval in reader permits arbitrary code execution."))
      (catch Exception ignored))))

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

;;;;;;;;;;;;;
;; Transit ;;
;;;;;;;;;;;;;

(defn stream-transit
  [fmt data]
  (let [out (ByteArrayOutputStream.)
        wrt (transit/writer out fmt)]
    (transit/write wrt data)
    (io/input-stream (.toByteArray out))))

(def transit-json-echo
  (wrap-transit-json-params identity))

(deftest augments-with-transit-json-content-type
  (let [req {:content-type "application/transit+json"
             :body (stream-transit :json {:foo "bar"})
             :params {"id" 3}}
             resp (transit-json-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def transit-msgpack-echo
  (wrap-transit-msgpack-params identity))

(deftest augments-with-transit-msgpack-content-type
  (let [req {:content-type "application/transit+msgpack"
             :body (stream-transit :msgpack {:foo "bar"})
             :params {"id" 3}}
             resp (transit-msgpack-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

;;;;;;;;;;;;;;;;;;;;
;; Restful Params ;;
;;;;;;;;;;;;;;;;;;;;

(def restful-echo
  (wrap-restful-params identity))

(def safe-restful-echo
  (wrap-restful-params identity
                       :handle-error (fn [_ _ _] {:status 500})))

(deftest test-restful-params-wrapper
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
             resp (restful-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))
    (is (= 500 (get (safe-restful-echo (assoc req :body (stream "{:foo \"bar}"))) :status)))))

(defn stream-iso [s]
  (ByteArrayInputStream. (.getBytes s "ISO-8859-1")))

(deftest test-different-params-charset
  (let [req {:content-type "application/clojure; charset=ISO-8859-1"
             :body (stream-iso "{:fée \"böz\"}")
             :params {"id" 3}}
        resp (restful-echo req)]
    (is (= {"id" 3 :fée "böz"} (:params resp)))
    (is (= {:fée "böz"} (:body-params resp)))))

(deftest test-list-body-request
  (let [req {:content-type "application/json"
             :body (ByteArrayInputStream.
                    (.getBytes "[\"gregor\", \"samsa\"]"))}]
    ((wrap-json-params
      (fn [{:keys [body-params]}] (is (= ["gregor" "samsa"] body-params))))
     req)))

(deftest test-optional-body
  ((wrap-json-params
    (fn [request]
      (is (nil? (:body request)))))
   {:body nil}))
