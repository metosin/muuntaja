(ns muuntaja.ring-middleware.format-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.format.yaml :as yaml-format]
            [clj-yaml.core :as yaml])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def api-echo
  (-> (fn [req]
        {:status 200
         :params (:params req)
         :body (:body-params req)})
      (middleware/wrap-params)
      (middleware/wrap-format)))

(def api-echo-json
  (-> (fn [req]
        {:status 200
         :params (:params req)
         :body (:body-params req)})
      (middleware/wrap-params)
      (middleware/wrap-format
        (-> m/default-options
            (m/select-formats ["application/json"])))))

(def api-echo-yaml
  (-> (fn [req]
        {:status 200
         :params (:params req)
         :body (:body-params req)})
      (middleware/wrap-params)
      (middleware/wrap-format
        (-> m/default-options
            (m/install yaml-format/format)
            (m/select-formats ["application/x-yaml"])))))

(deftest test-api-round-trip
  (let [ok-accept "application/edn"
        msg {:test :ok}
        r-trip (api-echo {:headers {"accept" ok-accept
                                    "content-type" ok-accept}
                          :body (stream (pr-str msg))})]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/edn; charset=utf-8"))
    (is (= (read-string (slurp (:body r-trip))) msg))
    (is (= (:params r-trip) msg))
    (is (.contains (get-in (api-echo {:headers {"accept" "foo/bar"
                                                "content-type" ok-accept}
                                      :body (stream (pr-str msg))})
                           [:headers "Content-Type"])
                   "application/json; charset=utf-8"))
    (is (api-echo {:headers {"accept" "foo/bar"}})))
  (let [ok-accept "application/json"
        msg {"test" "ok"}
        ok-req {:headers {"accept" ok-accept
                          "content-type" ok-accept}
                :body (stream (json/encode msg))}
        r-trip (api-echo-json ok-req)]
    (is (= (get-in r-trip [:headers "Content-Type"])
           "application/json; charset=utf-8"))
    (is (= (json/decode (slurp (:body r-trip))) msg))
    (is (= (:params r-trip) {:test "ok"}))
    (is (.contains (get-in (api-echo-json
                             {:headers {"accept" "application/edn"
                                        "content-type" "application/json"}
                              :body (stream (json/encode []))})
                           [:headers "Content-Type"])
                   "application/json")))
  (let [ok-accept "application/x-yaml"
        msg {"test" "ok"}
        ok-req {:headers {"accept" ok-accept
                          "content-type" ok-accept}
                :body (stream (yaml/generate-string msg))}
        r-trip (api-echo-yaml ok-req)]
    (is (= (:params r-trip) {:test "ok"}))))
