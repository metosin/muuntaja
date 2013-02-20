(ns ring.middleware.test.format
  (:use [clojure.test]
        [ring.middleware.format])
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def restful-echo
  (wrap-restful-format (fn [req] (assoc req :body (vals (:body-params req))))))

(def restful-echo-json
  (wrap-restful-format (fn [req] (assoc req :body (vals (:body-params req))))
                       :format [:json]))

(deftest test-restful-round-trip
  (let [ok-accept "application/edn"
        msg {:test :ok}
        ok-req {:headers {"accept" ok-accept}
                :content-type ok-accept
                :body (stream (pr-str msg))}
        r-trip (restful-echo ok-req)]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/edn; charset=utf-8"))
    (is (= (read-string (slurp (:body r-trip))) (vals msg)))
    (is (= (:params r-trip) msg))
    (is (thrown? RuntimeException (restful-echo {:headers {"accept" "foo/bar"}}))))
  (let [ok-accept "application/json"
        msg {"test" "ok"}
        ok-req {:headers {"accept" ok-accept}
                :content-type ok-accept
                :body (stream (json/encode msg))}
        r-trip (restful-echo-json ok-req)]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/json; charset=utf-8"))
    (is (= (json/decode (slurp (:body r-trip))) (vals msg)))
    (is (= (:params r-trip) msg))
    (is (nil? (:body-params (restful-echo-json {:headers {"accept" "application/edn"}
                                                :content-type "application/edn"}))))))
