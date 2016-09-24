(ns muuntaja.middleware-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]))

(defn echo [request]
  {:status 200
   :body (:body-params request)})

(defn ->request [content-type accept body]
  {:headers {"content-type" content-type
             "accept" accept}
   :body body})

(deftest middleware-test
  (let [m (m/create m/default-options)
        data {:kikka 42}]

    (testing "multiple way to initialize the middleware"
      (let [edn-string (m/encode m "application/edn" data)
            request (->request "application/edn" "application/edn" edn-string)]
        (is (= "{:kikka 42}" edn-string))
        (are [app]
          (= edn-string (:body (app request)))

          ;; without paramters
          (middleware/wrap-format echo)

          ;; with default options
          (middleware/wrap-format echo m/default-options)

          ;; with compiled muuntaja
          (middleware/wrap-format echo m))))

    (testing "with defaults"
      (let [app (middleware/wrap-format echo)]

        (testing "symmetric request decode + response encode"
          (are [format]
            (let [payload (m/encode m format data)
                  decode (partial m/decode m format)
                  request (->request format format payload)]
              (= data (-> request app :body decode)))
            "application/json"
            "application/edn"
            "application/x-yaml"
            "application/msgpack"
            "application/transit+json"
            "application/transit+msgpack"))

        (testing "content-type & accept"
          (let [json-string (m/encode m "application/json" data)
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
            (let [edn-string (m/encode m "application/edn" data)
                  yaml-string (m/encode m "application/x-yaml" data)
                  request (->request "application/edn" "application/x-yaml" edn-string)]
              (is (= yaml-string (-> request app :body))))))))

    (testing "runtime options for encoding & decoding"
      (testing "forcing a content-type on a handler (bypass negotiate)"
        (let [echo-edn (fn [request]
                         {:status 200
                          ::muuntaja/content-type "application/edn"
                          :body (:body-params request)})
              app (middleware/wrap-format echo-edn)
              request (->request "application/json" "application/json" "{\"kikka\":42}")
              response (-> request app)]
          (is (= "{:kikka 42}" (:body response)))
          (is (not (contains? response ::muuntaja/content-type)))
          (is (= "application/edn; charset=utf-8" (get-in response [:headers "Content-Type"]))))))))
