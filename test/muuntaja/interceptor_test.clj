(ns muuntaja.interceptor-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.interceptor :as interceptor]
            [sieppari.core]
            [muuntaja.core]))

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

(defn execute [interceptors request]
  (sieppari.core/execute interceptors request))

(deftest interceptor-test
  (let [m (m/create)
        data {:kikka 42}
        edn-string (slurp (m/encode m "application/edn" data))
        json-string (slurp (m/encode m "application/json" data))]

    (testing "multiple way to initialize the interceptor"
      (let [request (->request "application/edn" "application/edn" nil edn-string)]
        (is (= "{:kikka 42}" edn-string))
        (are [interceptors]
          (= edn-string (some-> (execute (conj interceptors echo) request) :body slurp))

          ;; without arguments
          [(interceptor/format-interceptor)]

          ;; with default options
          [(interceptor/format-interceptor m/default-options)]

          ;; with compiled muuntaja
          [(interceptor/format-interceptor m)]

          ;; without arguments
          [(interceptor/format-negotiate-interceptor)
           (interceptor/format-response-interceptor)
           (interceptor/format-request-interceptor)]

          ;; with default options
          [(interceptor/format-negotiate-interceptor m/default-options)
           (interceptor/format-response-interceptor m/default-options)
           (interceptor/format-request-interceptor m/default-options)]

          ;; with compiled muuntaja
          [(interceptor/format-negotiate-interceptor m)
           (interceptor/format-response-interceptor m)
           (interceptor/format-request-interceptor m)])))

    (testing "with defaults"
      (let [interceptors [(interceptor/format-interceptor) echo]]

        (testing "symmetric request decode + response encode"
          (are [format]
            (let [payload (m/encode m format data)
                  decode (partial m/decode m format)]
              (= data (->> (execute interceptors (->request format format nil payload))
                           :body
                           decode)))
            "application/json"
            "application/edn"
            ;"application/x-yaml"
            ;"application/msgpack"
            "application/transit+json"
            "application/transit+msgpack"))

        (testing "content-type & accept"
          (let [call (fn [content-type accept]
                       (some-> (execute interceptors (->request content-type accept nil json-string))
                               :body
                               slurp))]

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
              (is (= json-string (some-> (execute interceptors request) :body slurp))))))))

    (testing "with regexp matchers"
      (let [interceptors [(interceptor/format-interceptor
                            (assoc-in
                              m/default-options
                              [:formats "application/json" :matches]
                              #"^application/(.+\+)?json$"))
                          echo]]

        (testing "content-type & accept"
          (let [call (fn [content-type accept]
                       (some-> (execute interceptors (->request content-type accept nil json-string))
                               :body
                               slurp))]
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
      (let [interceptors [(interceptor/format-interceptor
                            (dissoc m/default-options :default-format))
                          echo]]
        (try
          (let [response (-> (execute interceptors (->request "application/json" nil nil json-string)) :body slurp)]
            (is (= response ::invalid)))
          (catch Exception e
            (is (= (-> e ex-data :type) :muuntaja/response-format-negotiation))))))

    (testing "without :default-charset"

      (testing "without valid request charset, request charset negotiation fails"
        (let [interceptors [(interceptor/format-interceptor
                              (dissoc m/default-options :default-charset))
                            echo]]
          (try
            (let [response (-> (execute interceptors (->request "application/json" nil nil json-string)) :body slurp)]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) :muuntaja/request-charset-negotiation))))))

      (testing "without valid accept charset, response charset negotiation fails"
        (let [interceptors [(interceptor/format-interceptor
                              (dissoc m/default-options :default-charset))
                            echo]]
          (try
            (let [response (-> (execute interceptors (->request "application/json; charset=utf-8" nil nil json-string)) :body slurp)]
              (is (= response ::invalid)))
            (catch Exception e
              (is (= (-> e ex-data :type) :muuntaja/response-charset-negotiation)))))))

    (testing "runtime options for encoding & decoding"
      (testing "forcing a content-type on a handler (bypass negotiate)"
        (let [echo-edn (fn [request]
                         {:status 200
                          :muuntaja/content-type "application/edn"
                          :body (:body-params request)})
              interceptors [(interceptor/format-interceptor) echo-edn]
              request (->request "application/json" "application/json" nil "{\"kikka\":42}")
              response (execute interceptors request)]
          (is (= "{:kikka 42}" (-> response :body slurp)))
          (is (not (contains? response :muuntaja/content-type)))
          (is (= "application/edn; charset=utf-8" (get-in response [:headers "Content-Type"]))))))

    (testing "different bodies"
      (let [m (m/create (assoc-in m/default-options [:http :encode-response-body?] (constantly true)))
            interceptors [(interceptor/format-interceptor m) echo]
            edn-request #(->request "application/edn" "application/edn" nil (pr-str %))
            e2e #(m/decode m "application/edn" (:body (execute interceptors (edn-request %))))]
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

(deftest params-interceptor-test
  (let [interceptors [(interceptor/params-interceptor) identity]]
    (is (= {:params {:a 1, :b {:c 1}}
            :body-params {:b {:c 1}}}
           (execute interceptors {:params {:a 1}
                                  :body-params {:b {:c 1}}})))
    (is (= {:params {:b {:c 1}}
            :body-params {:b {:c 1}}}
           (execute interceptors {:body-params {:b {:c 1}}})))
    (is (= {:body-params [1 2 3]}
           (execute interceptors {:body-params [1 2 3]})))))

(deftest exceptions-interceptor-test
  (let [->handler (fn [type]
                    (fn [_]
                      (condp = type
                        :decode (throw (ex-info "kosh" {:type :muuntaja/decode}))
                        :runtime (throw (RuntimeException.))
                        :return nil)))
        interceptors (fn [handler] [(interceptor/exception-interceptor) handler])]
    (is (nil? (execute (interceptors (->handler :return)) {})))
    (is (thrown? RuntimeException (execute (interceptors (->handler :runtime)) {})))
    (is (= 400 (:status (execute (interceptors (->handler :decode)) {}))))))
