(ns ring.middleware.format-response-test
  (:use [clojure.test]
        [ring.middleware.format-response])
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [cognitect.transit :as transit])
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

(deftest format-json-prettily
  (let [body {:foo "bar"}
        req {:body body}
        resp ((wrap-json-response identity :pretty true) req)]
    (is (.contains (slurp (:body resp)) "\n "))))

(deftest returns-correct-charset
  (let [body {:foo "bârçï"}
        req {:body body :headers {"accept-charset" "utf8; q=0.8 , utf-16"}}
        resp ((wrap-json-response identity) req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "utf-16"))
    (is (= 32 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(deftest returns-utf8-by-default
  (let [body {:foo "bârçï"}
        req {:body body :headers {"accept-charset" "foo"}}
        resp ((wrap-json-response identity) req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "utf-8"))
    (is (= 18 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

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

;;;;;;;;;;;;;
;; Transit ;;
;;;;;;;;;;;;;

(defn read-transit
  [fmt in]
  (let [rdr (transit/reader in fmt)]
    (transit/read rdr)))

(def transit-json-echo
  (wrap-transit-json-response identity))

(deftest format-transit-json-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (transit-json-echo req)]
    (is (= body (read-transit :json (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/transit+json"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def transit-msgpack-echo
  (wrap-transit-msgpack-response identity))

(deftest format-transit-msgpack-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (transit-msgpack-echo req)]
    (is (= body (read-transit :msgpack (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/transit+msgpack"))
    (is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Content-Type parsing ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                         :handle-error (fn [_ _ _] {:status 500})
                         :formats
                         [(make-encoder (fn [_] (throw (RuntimeException. "Memento mori")))
                                        "foo/bar")]))

(deftest format-hashmap-to-preferred
  (let [ok-accept "application/edn, application/json;q=0.5"
        ok-req {:headers {"accept" ok-accept}}]
    (is (= (get-in (restful-echo ok-req) [:headers "Content-Type"])
           "application/edn; charset=utf-8"))
    (is (.contains (get-in (restful-echo {:headers {"accept" "foo/bar"}})
                           [:headers "Content-Type"])
                   "application/json"))
    (is (= 500 (get (safe-restful-echo {:headers {"accept" "foo/bar"}}) :status)))))

(deftest format-restful-hashmap
  (let [body {:foo "bar"}]
    (doseq [accept ["application/edn"
                    "application/json"
                    "application/x-yaml"
                    "application/transit+json"
                    "application/transit+msgpack"
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
