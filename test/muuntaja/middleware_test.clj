(ns muuntaja.middleware-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.core])
  (:import (java.util Date)))

(defn echo [request]
  {:status 200
   :body (:body-params request)})

(defn async-echo [request respond _]
  (respond
    {:status 200
     :body (:body-params request)}))

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
              (middleware/wrap-format-request)
              (middleware/wrap-format-response)
              (middleware/wrap-format-negotiate))

          ;; with default options
          (-> echo
              (middleware/wrap-format-request m/default-options)
              (middleware/wrap-format-response m/default-options)
              (middleware/wrap-format-negotiate m/default-options))

          ;; with compiled muuntaja
          (-> echo
              (middleware/wrap-format-request m)
              (middleware/wrap-format-response m)
              (middleware/wrap-format-negotiate m)))))

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
            ;"application/x-yaml"
            ;"application/msgpack"
            "application/transit+json"
            "application/transit+msgpack"))

        (testing "auto-decoding response body"
          (let [data {:kikka (Date. 0)}
                app (middleware/wrap-format (constantly {:status 200, :body data}))]
            (are [format]
              (= data (-> {:headers {"accept" format}} app m/decode-response-body))
              ;"application/json"
              "application/edn"
              ;"application/x-yaml"
              ;"application/msgpack"
              "application/transit+json"
              "application/transit+msgpack")))

        (testing "failing auto-decoding response body"
          (testing "on decoding exception"
            (let [app (middleware/wrap-format
                        (constantly
                          {:status 200
                           :body (m/encode "application/edn" {:kikka 123})
                           :headers {"Content-Type" "application/json"}}))]
              (is (thrown-with-msg?
                    Exception
                    #"Malformed application/json response"
                    (m/decode-response-body (app {}))))))

          (testing "when no content-type is found"
            (let [app (middleware/wrap-format
                        (constantly
                          {:status 200
                           :body (m/encode "application/edn" {:kikka 123})}))]
              (is (thrown-with-msg?
                    Exception
                    #"No Content-Type found"
                    (m/decode-response-body (app {}))))))

          (testing "when no decoder is found"
            (let [app (middleware/wrap-format
                        (constantly
                          {:status 200
                           :body (m/encode "application/edn" {:kikka 123})
                           :headers {"Content-Type" "application/json2"}}))]
              (is (thrown-with-msg?
                    Exception
                    #"Unknown response Content-Type: application/json2"
                    (m/decode-response-body (app {})))))))

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
                  json-string (slurp (m/encode m "application/json" data))
                  request (->request "application/edn" "application/json" nil edn-string)]
              (is (= json-string (some-> request app :body slurp))))))))

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

    (testing "wildcards (name/*) & (*/*) successfully negotiate"
      (let [app (middleware/wrap-format echo (dissoc m/default-options :default-format))
            response  (app  (->request "application/json" "application/*" nil json-string))
            response' (app (->request "application/json" "*/*" nil json-string))]

        (is (= json-string (slurp (:body response))))
        (is (= json-string (slurp (:body response'))))))

    (testing "without :default-format & valid accept format, response format negotiation fails"
      (let [app (middleware/wrap-format echo (dissoc m/default-options :default-format))]
        (try
          (let [response (app (->request "application/json" nil nil json-string))]
            (is (= response ::invalid)))
          (catch Exception e
            (is (= (-> e ex-data :type) :muuntaja/response-format-negotiation))))))

    (testing "without :default-charset"

      (testing "without valid request charset, request charset negotiation fails"
        (let [app (middleware/wrap-format echo (dissoc m/default-options :default-charset))]
          (try
            (let [response (app (->request "application/json" nil nil json-string))]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) :muuntaja/request-charset-negotiation))))))

      (testing "without valid accept charset, response charset negotiation fails"
        (let [app (middleware/wrap-format echo (dissoc m/default-options :default-charset))]
          (try
            (let [response (app (->request "application/json; charset=utf-8" nil nil json-string))]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) :muuntaja/response-charset-negotiation)))))))

    (testing "runtime options for encoding & decoding"
      (testing "forcing a content-type on a handler (bypass negotiate)"
        (let [echo-edn (fn [request]
                         {:status 200
                          :muuntaja/content-type "application/edn"
                          :body (:body-params request)})
              app (middleware/wrap-format echo-edn)
              request (->request "application/json" "application/json" nil "{\"kikka\":42}")
              response (-> request app)]
          (is (= "{:kikka 42}" (-> response :body slurp)))
          (is (not (contains? response :muuntaja/content-type)))
          (is (= "application/edn; charset=utf-8" (get-in response [:headers "Content-Type"]))))))

    (testing "different bodies"
      (let [m (m/create (assoc-in m/default-options [:http :encode-response-body?] (constantly true)))
            app (middleware/wrap-format echo m)
            edn-request #(->request "application/edn" "application/edn" nil (pr-str %))
            e2e #(m/decode m "application/edn" (:body (app (edn-request %))))]
        (are [primitive]
          (is (= primitive (e2e primitive)))

          [:a 1]
          {:a 1}
          "kikka"
          :kikka
          true
          false
          nil
          1)))))

(deftest wrap-params-test
  (testing "sync"
    (let [mw (middleware/wrap-params identity)]
      (is (= {:params {:a 1, :b {:c 1}}
              :body-params {:b {:c 1}}}
             (mw {:params {:a 1}
                  :body-params {:b {:c 1}}})))
      (is (= {:params {:b {:c 1}}
              :body-params {:b {:c 1}}}
             (mw {:body-params {:b {:c 1}}})))
      (is (= {:body-params [1 2 3]}
             (mw {:body-params [1 2 3]})))))
  (testing "async"
    (let [mw (middleware/wrap-params (fn [request respond _] (respond request)))
          respond (promise), raise (promise)]
      (mw {:params {:a 1}
           :body-params {:b {:c 1}}} respond raise)
      (is (= {:params {:a 1, :b {:c 1}}
              :body-params {:b {:c 1}}}
             @respond)))))

(deftest wrap-exceptions-test
  (let [->handler (fn [type]
                    (fn
                      ([_]
                       (condp = type
                         :decode (throw (ex-info "kosh" {:type :muuntaja/decode}))
                         :runtime (throw (RuntimeException.))
                         :return nil))
                      ([_ respond raise]
                       (condp = type
                         :decode (raise (ex-info "kosh" {:type :muuntaja/decode}))
                         :runtime (raise (RuntimeException.))
                         :return (respond nil)))))
        ->mw (partial middleware/wrap-exception)]
    (testing "sync"
      (is (nil? ((->mw (->handler :return)) {})))
      (is (thrown? RuntimeException ((->mw (->handler :runtime)) {})))
      (is (= 400 (:status ((->mw (->handler :decode)) {})))))
    (testing "async"
      (let [respond (promise), raise (promise)]
        ((->mw (->handler :return)) {} respond raise)
        (is (nil? @respond)))
      (let [respond (promise), raise (promise)]
        ((->mw (->handler :runtime)) {} respond raise)
        (is (= RuntimeException (class @raise))))
      (let [respond (promise), raise (promise)]
        ((->mw (->handler :decode)) {} respond raise)
        (is (= 400 (:status @respond)))))))

(deftest async-normal
  (let [m (m/create)
        data {:kikka 42}
        json-string (slurp (m/encode m "application/json" data))]

    (testing "happy case"
      (let [app (middleware/wrap-format async-echo)
            respond (promise), raise (promise)]
        (app (->request "application/json" nil nil json-string) respond raise)
        (is (= (m/decode m "application/json" (:body @respond)) data))))))

(deftest negotiation-results-helpers
  (let [types (atom nil)
        app (middleware/wrap-format
              (fn [request]
                (reset! types [(m/get-request-format-and-charset request)
                               (m/get-response-format-and-charset request)])
                nil))]

    (testing "managed formats"
      (app {:headers {"content-type" "application/edn; charset=utf-16"
                      "accept" "application/transit+json, text/html, application/edn"
                      "accept-charset" "utf-16"}})
      (is (= [(m/map->FormatAndCharset
                {:charset "utf-16"
                 :format "application/edn"
                 :raw-format "application/edn"})
              (m/map->FormatAndCharset
                {:charset "utf-16"
                 :format "application/transit+json"
                 :raw-format "application/transit+json"})]
             @types)))

    (testing "pick default-charset if accepted, #79"
      (app {:headers {"content-type" "application/cheese; charset=utf-16"
                      "accept" "application/cake, text/html, application/edn"
                      "accept-charset" "x-ibm300, cheese/cake, utf-8, ibm775"}})
      (is (= [(m/map->FormatAndCharset
                {:charset "utf-16"
                 :format nil
                 :raw-format "application/cheese"})
              (m/map->FormatAndCharset
                {:charset "utf-8" ;; the default
                 :format "application/edn"
                 :raw-format "application/cake"})]
             @types)))

    (testing "non-managed formats"
      (app {:headers {"content-type" "application/cheese; charset=utf-16"
                      "accept" "application/cake, text/html, application/edn"
                      "accept-charset" "utf-16"}})
      (is (= [(m/map->FormatAndCharset
                {:charset "utf-16"
                 :format nil
                 :raw-format "application/cheese"})
              (m/map->FormatAndCharset
                {:charset "utf-16"
                 :format "application/edn"
                 :raw-format "application/cake"})]
             @types)))))
