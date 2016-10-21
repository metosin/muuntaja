(ns muuntaja.middleware-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.core]))

(defn echo [request]
  {:status 200
   :body (:body-params request)})

(defn ->request [content-type accept accept-charset body]
  {:headers {"content-type" content-type
             "accept-charset" accept-charset
             "accept" accept}
   :body body})

(deftest middleware-test
  (let [m (m/create)
        data {:kikka 42}
        edn-string (slurp (m/encode m "application/edn" data))
        json-string (slurp (m/encode m "application/json" data))]

    (testing "multiple way to initialize the middleware"
      (let [request (->request "application/edn" "application/edn" nil edn-string)]
        (is (= "{:kikka 42}" edn-string))
        (are [app]
          (= edn-string (slurp (:body (app request))))

          ;; without paramters
          (middleware/wrap-format echo)

          ;; with default options
          (middleware/wrap-format echo m/default-options)

          ;; with compiled muuntaja
          (middleware/wrap-format echo m)

          ;; without paramters
          (-> echo
              (middleware/wrap-request-format)
              (middleware/wrap-response-format)
              (middleware/wrap-negotiate-format))

          ;; with default options
          (-> echo
              (middleware/wrap-request-format m/default-options)
              (middleware/wrap-response-format m/default-options)
              (middleware/wrap-negotiate-format m/default-options))

          ;; with compiled muuntaja
          (-> echo
              (middleware/wrap-request-format m)
              (middleware/wrap-response-format m)
              (middleware/wrap-negotiate-format m)))))

    (testing "with defaults"
      (let [app (middleware/wrap-format echo)]

        (testing "symmetric request decode + response encode"
          (are [format]
            (let [payload (m/encode m format data)
                  decode (partial m/decode m format)
                  request (->request format format nil payload)]
              (= data (-> request app :body decode)))
            "application/json"
            "application/edn"
            "application/x-yaml"
            "application/msgpack"
            "application/transit+json"
            "application/transit+msgpack"))

        (testing "content-type & accept"
          (let [call (fn [content-type accept]
                       (some-> (->request content-type accept nil json-string) app :body slurp))]

            (is (= "{\"kikka\":42}" json-string))

            (testing "with content-type & accept"
              (is (= json-string (call "application/json" "application/json"))))

            (testing "without accept, :default-format is used in encode"
              (is (= json-string (call "application/json" nil))))

            (testing "without content-type, body is not parsed"
              (is (= nil (call nil nil))))

            (testing "different json content-type (regexp match) - don't match by default"
              (are [content-type]
                (nil? (call content-type nil))
                "application/json-patch+json"
                "application/vnd.foobar+json"
                "application/schema+json")))

          (testing "different content-type & accept"
            (let [edn-string (slurp (m/encode m "application/edn" data))
                  yaml-string (slurp (m/encode m "application/x-yaml" data))
                  request (->request "application/edn" "application/x-yaml" nil edn-string)]
              (is (= yaml-string (some-> request app :body slurp))))))))

    (testing "with regexp matchers"
      (let [app (middleware/wrap-format
                  echo
                  (-> m/default-options
                      (assoc-in [:formats "application/json" :matches] #"^application/(.+\+)?json$")))]

        (testing "content-type & accept"
          (let [call (fn [content-type accept]
                       (some-> (->request content-type accept nil json-string) app :body slurp))]

            (is (= "{\"kikka\":42}" json-string))

            (testing "different json content-type (regexp match)"
              (are [content-type]
                (= json-string (call content-type nil))
                "application/json"
                "application/json-patch+json"
                "application/vnd.foobar+json"
                "application/schema+json"))

            (testing "missing the regexp match"
              (are [content-type]
                (nil? (call content-type nil))
                "application/jsonz"
                "applicationz/+json"))))))

    (testing "without :default-format & valid accept format, response format negotiation fails"
      (let [app (middleware/wrap-format echo (dissoc m/default-options :default-format))]
        (try
          (let [response (app (->request "application/json" nil nil json-string))]
            (is (= response ::invalid)))
          (catch Exception e
            (is (= (-> e ex-data :type) ::m/response-format-negotiation))))))

    (testing "without :default-charset"

      (testing "without valid request charset, request charset negotiation fails"
        (let [app (middleware/wrap-format echo (dissoc m/default-options :default-charset))]
          (try
            (let [response (app (->request "application/json" nil nil json-string))]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) ::m/request-charset-negotiation))))))

      (testing "without valid request charset, a non-matching format is ok"
        (let [app (middleware/wrap-format echo (dissoc m/default-options :default-charset))]
          (let [response (app (->request nil nil nil json-string))]
            (is (= response {:status 200, :body nil})))))

      (testing "without valid accept charset, response charset negotiation fails"
        (let [app (middleware/wrap-format echo (dissoc m/default-options :default-charset))]
          (try
            (let [response (app (->request "application/json; charset=utf-8" nil nil json-string))]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) ::m/response-charset-negotiation)))))))

    (testing "runtime options for encoding & decoding"
      (testing "forcing a content-type on a handler (bypass negotiate)"
        (let [echo-edn (fn [request]
                         {:status 200
                          ::m/content-type "application/edn"
                          :body (:body-params request)})
              app (middleware/wrap-format echo-edn)
              request (->request "application/json" "application/json" nil "{\"kikka\":42}")
              response (-> request app)]
          (is (= "{:kikka 42}" (-> response :body slurp)))
          (is (not (contains? response ::m/content-type)))
          (is (= "application/edn; charset=utf-8" (get-in response [:headers "Content-Type"]))))))))
