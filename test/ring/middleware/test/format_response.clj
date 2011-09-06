(ns ring.middleware.test.format-response
  (:use [clojure.test]
        [ring.middleware.format-response]
        [clojure.contrib.io :only [slurp*]])
  (:require [clj-json.core :as json]
            [clj-yaml.core :as yaml])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def json-echo
  (wrap-json-response identity))

(deftest noop-with-string
  (let [body "<xml></xml>"
        req {:body body}
        resp (json-echo req)]
    (is (= body (:body resp)))))

(deftest noop-with-stream
  (let [body "<xml></xml>"
        req {:body (stream body)}
        resp (json-echo req)]
    (is (= body (slurp* (:body resp))))))

(deftest format-json-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (json-echo req)]
    (is (= (json/generate-string body) (slurp* (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def clojure-echo
  (wrap-clojure-response identity))

(deftest format-clojure-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (clojure-echo req)]
    (is (= body (read-string (slurp* (:body resp)))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/clojure"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def yaml-echo
  (wrap-yaml-response identity))

(deftest format-yaml-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (yaml-echo req)]
    (is (= (yaml/generate-string body) (slurp* (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/x-yaml"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def restful-echo
  (wrap-restful-response identity))

(deftest format-restful-hashmap
  (let [body {:foo "bar"}]
    (doseq [accept ["application/clojure" "application/json" "application/x-yaml" "text/html"]]
      (let [req {:body body :headers {"accept" (str accept "; charset=utf-8")}}
            resp (restful-echo req)]
        (is (.contains (get-in resp [:headers "Content-Type"]) accept))
        (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
    (let [req {:body body}
          resp (restful-echo req)]
      (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
      (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"])))))))

(def custom-restful-echo
  (wrap-restful-response identity :default wrap-clojure-response))

(deftest format-custom-restful-hashmap
  (let [req {:body {:foo "bar"}}
        resp (custom-restful-echo req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/clojure"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
