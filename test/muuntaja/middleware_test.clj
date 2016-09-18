(ns muuntaja.middleware-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as muuntaja]
            [muuntaja.middleware :as middleware]))

(defn echo [request]
  {:status 200
   :body (:body-params request)})

(defn ->request [content-type accept body]
  {:request-method :get
   :uri "/anything"
   :headers {"content-type" content-type
             "accept" accept}
   :body body})

(deftest middleware-test
  (let [muuntaja (muuntaja/compile muuntaja/default-options)
        data {:kikka 42}]

    (testing "multiple way to initialize the middleware"
      (let [edn-string (muuntaja/encode muuntaja :edn data)
            request (->request "application/edn" "application/edn" edn-string)]
        (is (= "{:kikka 42}" edn-string))
        (are [app]
          (do
            (= edn-string (:body (app request))))

          ;; without paramters
          (middleware/wrap-format echo)

          ;; with default options
          (middleware/wrap-format echo muuntaja/default-options)

          ;; with compiled muuntaja
          (middleware/wrap-format echo muuntaja))))

    (testing "with defaults"
      (let [app (middleware/wrap-format echo)]

        (testing "symmetric request decode + response encode"
          (are [format]
            (let [payload (muuntaja/encode muuntaja format data)
                  decode (partial muuntaja/decode muuntaja format)
                  content-type (get-in muuntaja [:produces format])
                  request (->request content-type content-type payload)]
              (= data (-> request app :body decode)))
            :json :edn :yaml :msgpack :transit-json :transit-msgpack))

        (testing "content-type & accept"
          (let [json-string (muuntaja/encode muuntaja :json data)
                call (fn [content-type accept]
                       (-> (->request content-type accept json-string) app :body))]

            (is (= "{\"kikka\":42}" json-string))

            (testing "with content-type & accept"
              (is (= json-string (call "application/json" "application/json"))))

            (testing "without accept, first format (JSON) is used in encode"
              (is (= json-string (call "application/json" nil))))

            (testing "without content-type, body is not parsed"
              (is (= nil (call nil nil))))

            (testing "different json content-type (regexp match)"
              (are [content-type]
                (= json-string (call content-type nil))
                "application/json"
                "application/json-patch+json"
                "application/vnd.foobar+json"
                "application/schema+json")))

          (testing "different content-type & accept"
            (let [edn-string (muuntaja/encode muuntaja :edn data)
                  yaml-string (muuntaja/encode muuntaja :yaml data)
                  request (->request "application/edn" "application/x-yaml" edn-string)]
              (is (= yaml-string (-> request app :body))))))))))
