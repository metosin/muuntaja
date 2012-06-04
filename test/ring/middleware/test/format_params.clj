(ns ring.middleware.test.format-params
  (:use [clojure.test]
        [ring.middleware.format-params])
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml])
  (:import [java.io ByteArrayInputStream]))

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
    (is (= "<xml></xml>") (slurp (:body resp)))
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

(def restful-echo
  (wrap-restful-params identity))

(deftest test-restful-params-wrapper
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
             resp (restful-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(defn stream-iso [s]
  (ByteArrayInputStream. (.getBytes s "ISO-8859-1")))

(deftest test-different-params-charset
  (let [req {:content-type "application/clojure; charset=ISO-8859-1"
             :body (stream-iso "{:fée \"böz\"}")
             :params {"id" 3}}
        resp (restful-echo req)]
    (is (= {"id" 3 :fée "böz"} (:params resp)))
    (is (= {:fée "böz"} (:body-params resp)))))
