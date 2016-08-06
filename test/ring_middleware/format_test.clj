(ns ring-middleware.format-test
  (:use [clojure.test]
        [ring-middleware.format])
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def api-echo
  (wrap-api-format (fn [req] (assoc req :body (vals (:body-params req))))))

(def api-echo-json
  (wrap-api-format
    (fn [req] (assoc req :body (vals (:body-params req))))
    {:formats [:json-kw]}))

(def api-echo-yaml
  (wrap-api-format
    (fn [req] (assoc req :body (vals (:body-params req))))
    {:formats [:yaml-kw]}))

(deftest test-api-round-trip
  (let [ok-accept "application/edn"
        msg {:test :ok}
        ok-req {:headers {"accept" ok-accept}
                :content-type ok-accept
                :body (stream (pr-str msg))}
        r-trip (api-echo ok-req)]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/edn; charset=utf-8"))
    (is (= (read-string (slurp (:body r-trip))) (vals msg)))
    (is (= (:params r-trip) msg))
    (is (.contains (get-in (api-echo {:headers {"accept" "foo/bar"}})
                           [:headers "Content-Type"])
                   "application/json"))
    (is (api-echo {:headers {"accept" "foo/bar"}})))
  (let [ok-accept "application/json"
        msg {"test" "ok"}
        ok-req {:headers {"accept" ok-accept}
                :content-type ok-accept
                :body (stream (json/encode msg))}
        r-trip (api-echo-json ok-req)]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/json; charset=utf-8"))
    (is (= (json/decode (slurp (:body r-trip))) (vals msg)))
    (is (= (:params r-trip) {:test "ok"}))
    (is (.contains (get-in (api-echo-json
                             {:headers {"accept" "application/edn"}
                              :content-type "application/edn"})
                           [:headers "Content-Type"])
                   "application/json")))
  (let [ok-accept "application/x-yaml"
        msg {"test" "ok"}
        ok-req {:headers {"accept" ok-accept}
                :content-type ok-accept
                :body (stream (yaml/generate-string msg))}
        r-trip (api-echo-yaml ok-req)]
    (is (= (:params r-trip) {:test "ok"}))))
