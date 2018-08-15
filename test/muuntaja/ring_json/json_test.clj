(ns muuntaja.ring-json.json-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [cheshire.parse]
            [ring.util.io :refer [string-input-stream]]))

(def +handler+ (fn [{:keys [body-params body]}]
                 {:body (or body-params body)}))

;;
;; create ring-json middlewares using muuntaja
;;

(defn wrap-json-params
  ([handler]
   (wrap-json-params handler {}))
  ([handler opts]
   (-> handler
       (middleware/wrap-params)
       (middleware/wrap-format
         (-> m/default-options
             (m/select-formats
               ["application/json"])
             (update-in
               [:formats "application/json"]
               merge
               {:matches #"^application/(.+\+)?json$"
                :encoder nil
                :decoder-opts (merge {:decode-key-fn false} opts)})))
       (middleware/wrap-exception (constantly
                                    (or
                                      (:malformed-response opts)
                                      {:status 400
                                       :headers {"Content-Type" "text/plain"}
                                       :body "Malformed JSON in request body."}))))))

(defn wrap-params [handler]
  (fn [request]
    (let [body-params (:body-params request)]
      (handler (assoc request :json-params body-params)))))

(defn wrap-json-response
  ([handler]
   (wrap-json-response handler {}))
  ([handler opts]
   (-> handler
       (middleware/wrap-format
         (-> m/default-options
             (m/transform-formats #(dissoc %2 :decoder))
             (assoc-in
               [:formats "application/json" :encoder-opts]
               opts)))
       (middleware/wrap-exception (constantly
                                    (or
                                      (:malformed-response opts)
                                      {:status 400
                                       :headers {"Content-Type" "text/plain"}
                                       :body "Malformed JSON in request body."}))))))

;;
;; tests
;;

(deftest test-json-body
  (let [wrap (wrap-json-params +handler+)]
    (testing "xml body"
      (let [request {:headers {"content-type" "application/xml"}
                     :body (string-input-stream "<xml></xml>")}
            response (wrap request)]
        (is (= "<xml></xml>" (slurp (:body response))))))

    (testing "json body"
      (let [request {:headers {"content-type" "application/json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"}")}
            response (wrap request)]
        (is (= {"foo" "bar"} (:body response)))))

    (testing "custom json body"
      (let [request {:headers {"content-type" "application/vnd.foobar+json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"}")}
            response (wrap request)]
        (is (= {"foo" "bar"} (:body response)))))

    (testing "json patch body"
      (let [json-string "[{\"op\": \"add\",\"path\":\"/foo\",\"value\": \"bar\"}]"
            request {:headers {"content-type" "application/json-patch+json; charset=UTF-8"}
                     :body (string-input-stream json-string)}
            response (wrap request)]
        (is (= [{"op" "add" "path" "/foo" "value" "bar"}] (:body response)))))

    (testing "malformed json"
      (let [request {:headers {"content-type" "application/json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"")}]
        (is (= (wrap request)
               {:status 400
                :headers {"Content-Type" "text/plain"}
                :body "Malformed JSON in request body."})))))

  (let [handler (wrap-json-params +handler+ {:decode-key-fn true})]
    (testing "keyword keys"
      (let [request {:headers {"content-type" "application/json"}
                     :body (string-input-stream "{\"foo\": \"bar\"}")}
            response (handler request)]
        (is (= {:foo "bar"} (:body response))))))

  (let [handler (wrap-json-params +handler+ {:decode-key-fn true, :bigdecimals true})]
    (testing "bigdecimal floats"
      (let [request {:headers {"content-type" "application/json"}
                     :body (string-input-stream "{\"foo\": 5.5}")}
            response (handler request)]
        (is (decimal? (-> response :body :foo)))
        (is (= {:foo 5.5M} (:body response))))))

  (testing "custom malformed json"
    (let [malformed {:status 400
                     :headers {"Content-Type" "text/html"}
                     :body "<b>Your JSON is wrong!</b>"}
          handler (wrap-json-params +handler+ {:malformed-response malformed})
          request {:headers {"content-type" "application/json"}
                   :body (string-input-stream "{\"foo\": \"bar\"")}]
      (is (= (handler request) malformed))))

  (let [handler (fn [_] {:status 200 :headers {} :body {:bigdecimals cheshire.parse/*use-bigdecimals?*}})]
    (testing "don't overwrite bigdecimal binding"
      (binding [cheshire.parse/*use-bigdecimals?* false]
        (let [response ((wrap-json-params handler {:bigdecimals true}) {})]
          (is (= (get-in response [:body :bigdecimals]) false))))
      (binding [cheshire.parse/*use-bigdecimals?* true]
        (let [response ((wrap-json-params handler {:bigdecimals false}) {})]
          (is (= (get-in response [:body :bigdecimals]) true)))))))

(deftest test-json-params
  (let [handler (-> identity wrap-params wrap-json-params)]
    (testing "xml body"
      (let [request {:headers {"content-type" "application/xml"}
                     :body (string-input-stream "<xml></xml>")
                     :params {"id" 3}}
            response (handler request)]
        (is (= "<xml></xml>" (slurp (:body response))))
        (is (= {"id" 3} (:params response)))
        (is (nil? (:json-params response)))))

    (testing "json body"
      (let [request {:headers {"content-type" "application/json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"}")
                     :params {"id" 3}}
            response (handler request)]
        (is (= {"id" 3, "foo" "bar"} (:params response)))
        (is (= {"foo" "bar"} (:json-params response)))))

    (testing "json body with bigdecimals"
      (let [handler (-> identity wrap-params (wrap-json-params {:bigdecimals true}))
            request {:headers {"content-type" "application/json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": 5.5}")
                     :params {"id" 3}}
            response (handler request)]
        (is (decimal? (get-in response [:params "foo"])))
        (is (decimal? (get-in response [:json-params "foo"])))
        (is (= {"id" 3, "foo" 5.5M} (:params response)))
        (is (= {"foo" 5.5M} (:json-params response)))))

    (testing "custom json body"
      (let [request {:headers {"content-type" "application/vnd.foobar+json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"}")
                     :params {"id" 3}}
            response (handler request)]
        (is (= {"id" 3, "foo" "bar"} (:params response)))
        (is (= {"foo" "bar"} (:json-params response)))))

    (testing "json schema body"
      (let [request {:headers {"content-type" "application/schema+json; charset=UTF-8"}
                     :body (string-input-stream "{\"type\": \"schema\",\"properties\":{}}")
                     :params {"id" 3}}
            response (handler request)]
        (is (= {"id" 3, "type" "schema", "properties" {}} (:params response)))
        (is (= {"type" "schema", "properties" {}} (:json-params response)))))

    (testing "array json body"
      (let [request {:headers {"content-type" "application/vnd.foobar+json; charset=UTF-8"}
                     :body (string-input-stream "[\"foo\"]")
                     :params {"id" 3}}
            response (handler request)]
        (is (= {"id" 3} (:params response)))))

    (testing "malformed json"
      (let [request {:headers {"content-type" "application/json; charset=UTF-8"}
                     :body (string-input-stream "{\"foo\": \"bar\"")}]
        (is (= (handler request)
               {:status 400
                :headers {"Content-Type" "text/plain"}
                :body "Malformed JSON in request body."})))))

  (testing "custom malformed json"
    (let [malformed {:status 400
                     :headers {"Content-Type" "text/html"}
                     :body "<b>Your JSON is wrong!</b>"}
          handler (wrap-json-params identity {:malformed-response malformed})
          request {:headers {"content-type" "application/json"}
                   :body (string-input-stream "{\"foo\": \"bar\"")}]
      (is (= (handler request) malformed))))

  (testing "don't overwrite bigdecimal binding"
    (let [handler (fn [_] {:status 200 :headers {} :body {:bigdecimals cheshire.parse/*use-bigdecimals?*}})]
      (binding [cheshire.parse/*use-bigdecimals?* false]
        (let [response ((wrap-json-params handler {:bigdecimals true}) {})]
          (is (= (get-in response [:body :bigdecimals]) false))))
      (binding [cheshire.parse/*use-bigdecimals?* true]
        (let [response ((wrap-json-params handler {:bigdecimals false}) {})]
          (is (= (get-in response [:body :bigdecimals]) true)))))))

(deftest test-json-response
  (testing "map body"
    (let [handler (constantly {:status 200 :headers {} :body {:foo "bar"}})
          response ((wrap-json-response handler) {})
          body (-> response :body slurp)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; charset=utf-8"))
      (is (= body "{\"foo\":\"bar\"}"))))

  (testing "string body"
    (let [handler (constantly {:status 200 :headers {} :body "foobar"})
          response ((wrap-json-response handler) {})]
      (is (= (:headers response) {}))
      (is (= (:body response) "foobar"))))

  (testing "vector body"
    (let [handler (constantly {:status 200 :headers {} :body [:foo :bar]})
          response ((wrap-json-response handler) {})
          body (-> response :body slurp)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; charset=utf-8"))
      (is (= body "[\"foo\",\"bar\"]"))))

  (testing "list body"
    (let [handler (constantly {:status 200 :headers {} :body '(:foo :bar)})
          response ((wrap-json-response handler) {})
          body (-> response :body slurp)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; charset=utf-8"))
      (is (= body "[\"foo\",\"bar\"]"))))

  (testing "set body"
    (let [handler (constantly {:status 200 :headers {} :body #{:foo :bar}})
          response ((wrap-json-response handler) {})
          body (-> response :body slurp)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; charset=utf-8"))
      (is (or (= body "[\"foo\",\"bar\"]")
              (= body "[\"bar\",\"foo\"]")))))

  (testing "JSON options"
    (let [handler (constantly {:status 200 :headers {} :body {:foo "bar" :baz "quz"}})
          response ((wrap-json-response handler {:pretty true}) {})
          body (-> response :body slurp)]
      (is (or (= body "{\n  \"foo\" : \"bar\",\n  \"baz\" : \"quz\"\n}")
              (= body "{\n  \"baz\" : \"quz\",\n  \"foo\" : \"bar\"\n}")))))

  (testing "CHANGED: don’t overwrite Content-Type if already set - format if :muuntaja/encode is truthy"
    (let [handler (constantly {:status 200 :headers {"Content-Type" "application/json; some-param=some-value"} :body {:foo "bar"}, :muuntaja/encode true})
          response ((wrap-json-response handler) {})
          body (-> response :body slurp)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; some-param=some-value"))
      (is (= body "{\"foo\":\"bar\"}"))))

  (testing "CHANGED: don’t overwrite Content-Type if already set - don't format if :muuntaja/encode is not set"
    (let [handler (constantly {:status 200 :headers {"Content-Type" "application/json; some-param=some-value"} :body {:foo "bar"}})
          response ((wrap-json-response handler) {})
          body (-> response :body)]
      (is (= (get-in response [:headers "Content-Type"]) "application/json; some-param=some-value"))
      (is (= body {:foo "bar"})))))
