(ns ring.middleware.test.format-response
  (:use [clojure.test]
        [ring.middleware.format-response])
  (:require [cheshire.core :as json]
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
    (is (= body (slurp (:body resp))))))

(deftest format-json-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (json-echo req)]
    (is (= (json/generate-string body) (slurp (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def clojure-echo
  (wrap-clojure-response identity))

(deftest format-clojure-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (clojure-echo req)]
    (is (= body (read-string (slurp (:body resp)))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/edn"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def yaml-echo
  (wrap-yaml-response identity))

(deftest format-yaml-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (yaml-echo req)]
    (is (= (yaml/generate-string body) (slurp (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/x-yaml"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(deftest can-encode?-accept-any-type
  (is (can-encode? {:enc-type {:type "foo" :sub-type "bar"}}
                   {:type "*" :sub-type "*"})))

(deftest can-encode?-accept-any-sub-type
  (let [encoder {:enc-type {:type "foo" :sub-type "bar"}}]
    (is (can-encode? encoder
                     {:type "foo" :sub-type "*"}))
    (is (not (can-encode? encoder
                          {:type "foo" :sub-type "buzz"})))))

(deftest can-encode?-accept-specific-type
  (let [encoder {:enc-type {:type "foo" :sub-type "bar"}}]
    (is (can-encode? encoder
                     {:type "foo" :sub-type "bar"}))
    (is (not (can-encode? encoder
                          {:type "foo" :sub-type "buzz"})))))

(deftest orders-values-correctly
  (let [accept "text/plain, */*, text/plain;level=1, text/*, text/*;q=0.1"]
    (is (= (parse-accept-header accept)
           (list {:type "text"
                  :sub-type "plain"
                  :parameter "level=1"
                  :q 1.0}
                 {:type "text"
                  :sub-type "plain"
                  :q 1.0}
                 {:type "text"
                  :sub-type "*"
                  :q 1.0}
                 {:type "*"
                  :sub-type "*"
                  :q 1.0}
                 {:type "text"
                  :sub-type "*"
                  :q 0.1})))))

(deftest gives-preferred-encoder
  (let [accept [{:type "text"
                 :sub-type "*"}
                {:type "application"
                 :sub-type "json"
                 :q 0.5}]
        req {:headers {"accept" accept}}
        html-encoder {:enc-type {:type "text" :sub-type "html"}}
        json-encoder {:enc-type {:type "application" :sub-type "json"}}]
    (is (= (preferred-encoder [json-encoder html-encoder] req)
           html-encoder))
    (is (= (preferred-encoder [json-encoder html-encoder] {})
           json-encoder))
    (is (nil? (preferred-encoder [{:enc-type {:type "application"
                                              :sub-type "edn"}}]
                                 req)))))

(def restful-echo
  (wrap-restful-response identity))

(def safe-restful-echo
  (wrap-restful-response identity
                         :handle-error (fn [_ _ _] {:status 500})))

(deftest format-hashmap-to-preferred
  (let [ok-accept "application/edn, application/json;q=0.5"
        ok-req {:headers {"accept" ok-accept}}]
    (is (= (get-in (restful-echo ok-req) [:headers "Content-Type"])
           "application/edn; charset=utf-8"))
    (is (thrown? RuntimeException (restful-echo {:headers {"accept" "foo/bar"}})))
    (is (= 500 (get (safe-restful-echo {:headers {"accept" "foo/bar"}}) :status)))))

(deftest format-restful-hashmap
  (let [body {:foo "bar"}]
    (doseq [accept ["application/edn"
                    "application/json"
                    "application/x-yaml"
                    "text/html"]]
      (let [req {:body body :headers {"accept" accept}}
            resp (restful-echo req)]
        (is (.contains (get-in resp [:headers "Content-Type"]) accept))
        (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
    (let [req {:body body}
          resp (restful-echo req)]
      (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
      (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"])))))))

(def custom-restful-echo
  (wrap-restful-response identity
                         :formats [{:encoder (constantly "foobar")
                                    :enc-type {:type "text"
                                               :sub-type "foo"}}]))

(deftest format-custom-restful-hashmap
  (let [req {:body {:foo "bar"} :headers {"accept" "text/foo"}}
        resp (custom-restful-echo req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "text/foo"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
