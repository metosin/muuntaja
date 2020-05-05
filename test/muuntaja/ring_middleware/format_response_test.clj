(ns muuntaja.ring-middleware.format-response-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.walk :refer [keywordize-keys]]
            [cognitect.transit :as transit]
            [muuntaja.format.msgpack :as msgpack-format]
            [muuntaja.format.yaml :as yaml-format]
            [msgpack.core :as msgpack]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [muuntaja.format.core :as format])
  (:import [java.io ByteArrayInputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(defn wrap-api-response
  ([handler]
   (wrap-api-response handler m/default-options))
  ([handler opts]
   (-> handler
       (middleware/wrap-format
         (m/transform-formats
           (-> opts
               (m/install yaml-format/format)
               (m/install msgpack-format/format))
           #(dissoc %2 :decoder))))))

(def api-echo
  (wrap-api-response identity))

(deftest noop-with-string
  (let [body "<xml></xml>"
        req {:body body}
        resp (api-echo req)]
    (is (= body (:body resp)))))

(deftest noop-with-stream
  (let [body "<xml></xml>"
        req {:body (stream body)}
        resp (api-echo req)]
    (is (= body (slurp (:body resp))))))

(deftest format-json-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (api-echo req)]
    (is (= (json/generate-string body) (slurp (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(deftest format-json-prettily
  (let [body {:foo "bar"}
        req {:body body}
        resp ((wrap-api-response
                identity
                (-> m/default-options
                    (m/select-formats ["application/json"])
                    (assoc-in
                      [:formats "application/json" :encoder-opts]
                      {:pretty true}))) req)]
    (is (.contains (slurp (:body resp)) "\n "))))

(deftest returns-correct-charset
  (testing "with fixed charset"
    (let [body {:foo "bârçï"}
          req {:body body :headers {"accept-charset" "utf8; q=0.8 , utf-16"}}
          resp ((-> identity
                    (wrap-api-response
                      (assoc m/default-options :charsets #{"utf-8"}))) req)]
      (is (not (.contains (get-in resp [:headers "Content-Type"]) "utf-16")))
      #_(is (not= 32 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
  (testing "with defaults charsets "
    (let [body {:foo "bârçï"}
          req {:body body :headers {"accept-charset" "utf8; q=0.8 , utf-16"}}
          resp ((-> identity
                    (wrap-api-response m/default-options)) req)]
      (is (.contains (get-in resp [:headers "Content-Type"]) "utf-16"))
      #_(is (= 32 (Integer/parseInt (get-in resp [:headers "Content-Length"])))))))

(deftest returns-utf8-by-default
  (let [body {:foo "bârçï"}
        req {:body body :headers {"accept-charset" "foo"}}
        resp ((wrap-api-response identity) req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "utf-8"))
    #_(is (= 18 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(deftest format-json-options
  (let [body {:foo-bar "bar"}
        req {:body body}
        resp2 ((-> identity
                   (wrap-api-response
                     (-> m/default-options
                         (assoc-in
                           [:formats "application/json" :encoder-opts]
                           {:encode-key-fn (comp str/upper-case name)}))))
               req)]
    (is (= "{\"FOO-BAR\":\"bar\"}"
           (slurp (:body resp2))))))

(def msgpack-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/install msgpack-format/format)
        (m/select-formats ["application/msgpack"]))))

(deftest format-msgpack-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (msgpack-echo req)]
    (is (= body (keywordize-keys (msgpack/unpack (:body resp)))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/msgpack"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def clojure-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/select-formats ["application/edn"]))))

(deftest format-clojure-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (clojure-echo req)]
    (is (= body (read-string (slurp (:body resp)))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/edn"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def yaml-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/install yaml-format/format)
        (m/select-formats ["application/x-yaml"]))))

(deftest format-yaml-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (yaml-echo req)]
    (is (= (yaml/generate-string body) (slurp (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/x-yaml"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

;; not implemented
#_(deftest html-escape-yaml-in-html
    (let [req {:body {:foo "<bar>"}}
          resp ((rmfr/wrap-api-response identity {:formats [:yaml-in-html]}) req)
          body (slurp (:body resp))]
      (is (= "<html>\n<head></head>\n<body><div><pre>\n{foo: &lt;bar&gt;}\n</pre></div></body></html>" body))))

;; Transit

(defn read-transit
  [fmt in]
  (let [rdr (transit/reader (io/input-stream in) fmt)]
    (transit/read rdr)))

(def transit-json-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/select-formats ["application/transit+json"]))))

(deftest format-transit-json-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (transit-json-echo req)]
    (is (= body (read-transit :json (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/transit+json"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(def transit-msgpack-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/select-formats ["application/transit+msgpack"]))))

(deftest format-transit-msgpack-hashmap
  (let [body {:foo "bar"}
        req {:body body}
        resp (transit-msgpack-echo req)]
    (is (= body (read-transit :msgpack (:body resp))))
    (is (.contains (get-in resp [:headers "Content-Type"]) "application/transit+msgpack"))
    ;; we do not set the "Content-Length"
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

#_(comment
    ;; Content-Type parsing

    (deftest can-encode?-accept-any-type
      (is (#'rmfr/can-encode? {:enc-type {:type "foo" :sub-type "bar"}}
            {:type "*" :sub-type "*"})))

    (deftest can-encode?-accept-any-sub-type
      (let [encoder {:enc-type {:type "foo" :sub-type "bar"}}]
        (is (#'rmfr/can-encode? encoder
              {:type "foo" :sub-type "*"}))
        (is (not (#'rmfr/can-encode? encoder
                   {:type "foo" :sub-type "buzz"})))))

    (deftest can-encode?-accept-specific-type
      (let [encoder {:enc-type {:type "foo" :sub-type "bar"}}]
        (is (#'rmfr/can-encode? encoder
              {:type "foo" :sub-type "bar"}))
        (is (not (#'rmfr/can-encode? encoder
                   {:type "foo" :sub-type "buzz"})))))

    (deftest orders-values-correctly
      (let [accept "text/plain, */*, text/plain;level=1, text/*, text/*;q=0.1"]
        (is (= (#'rmfr/parse-accept-header accept)
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
        (is (= (#'rmfr/preferred-adapter [json-encoder html-encoder] req)
               html-encoder))
        (is (nil? (#'rmfr/preferred-adapter [json-encoder html-encoder] {})))
        (is (nil? (#'rmfr/preferred-adapter [{:enc-type {:type "application"
                                                         :sub-type "edn"}}]
                    req))))))

(comment

  (def safe-api-echo-opts-map
    (rmfr/wrap-api-response identity
                            {:handle-error (fn [_ _ _] {:status 500})
                             :formats [{:content-type "foo/bar"
                                        :encoder (fn [_] (throw (RuntimeException. "Memento mori")))}]}))

  (deftest format-hashmap-to-preferred
    (let [ok-accept "application/edn, application/json;q=0.5"
          ok-req {:headers {"accept" ok-accept}}]
      (is (= (get-in (api-echo ok-req) [:headers "Content-Type"])
             "application/edn; charset=utf-8"))
      (is (.contains (get-in (api-echo {:headers {"accept" "foo/bar"}})
                             [:headers "Content-Type"])
                     "application/json"))
      (is (= 500 (get (safe-api-echo-opts-map {:status 200
                                               :headers {"accept" "foo/bar"}
                                               :body {}}) :status))))))

(deftest format-api-hashmap
  (let [body {:foo "bar"}]
    (doseq [accept ["application/edn"
                    "application/json"
                    "application/msgpack"
                    "application/x-yaml"
                    "application/transit+json"
                    "application/transit+msgpack"
                    #_"text/html"]]
      (let [req {:body body :headers {"accept" accept}}
            resp (api-echo req)]
        (is (.contains (get-in resp [:headers "Content-Type"]) accept))
        #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))
    (let [req {:body body}
          resp (api-echo req)]
      (is (.contains (get-in resp [:headers "Content-Type"]) "application/json"))
      #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"])))))))

(def custom-api-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/install {:name "text/foo"
                    :encoder (reify
                               format/EncodeToBytes
                               (encode-to-bytes [_ _ _]
                                 (.getBytes "foobar")))}))))

(deftest format-custom-api-hashmap
  (let [req {:body {:foo "bar"} :headers {"accept" "text/foo"}}
        resp (custom-api-echo req)]
    (is (.contains (get-in resp [:headers "Content-Type"]) "text/foo"))
    #_(is (< 2 (Integer/parseInt (get-in resp [:headers "Content-Length"]))))))

(deftest nil-body-handling
  (let [req {:body {:headers {"accept" "application/json"}}}
        handler (-> (constantly {:status 200
                                 :headers {}})
                    wrap-api-response)
        resp (handler req)]
    (is (nil? (get-in resp [:headers "Content-Type"])))
    (is (nil? (get-in resp [:headers "Content-Length"])))
    (is (nil? (:body resp)))))

(def api-echo-pred
  (wrap-api-response
    identity
    (-> m/default-options
        (assoc-in [:http :encode-response-body?] ::serializable?))))

(deftest custom-predicate
  (let [req {:body {:foo "bar"}}
        resp-non-serialized (api-echo-pred (assoc req ::serializable? false))
        resp-serialized (api-echo-pred (assoc req ::serializable? true))]
    (is (map? (:body resp-non-serialized)))
    (is (= "{\"foo\":\"bar\"}" (slurp (:body resp-serialized))))))

(def custom-encoder
  (get-in m/default-options [:formats "application/json"]))

(def custom-content-type
  (wrap-api-response
    (fn [_]
      {:status 200
       :body {:foo "bar"}})
    (-> m/default-options
        (assoc-in [:formats "application/vnd.mixradio.something+json"] custom-encoder)
        (m/select-formats ["application/vnd.mixradio.something+json" "application/json"]))))

(deftest custom-content-type-test
  (let [resp (custom-content-type {:body {:foo "bar"} :headers {"accept" "application/vnd.mixradio.something+json"}})]
    (is (= "application/vnd.mixradio.something+json; charset=utf-8" (get-in resp [:headers "Content-Type"])))))

;; Transit options

(defrecord Point [x y])

(def writers
  {Point (transit/write-handler (constantly "Point") (fn [p] [(:x p) (:y p)]))})

(def custom-transit-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (m/select-formats ["application/transit+json"])
        (assoc-in
          [:formats "application/transit+json" :encoder-opts]
          {:handlers writers}))))

(def custom-api-transit-echo
  (wrap-api-response
    identity
    (-> m/default-options
        (assoc-in
          [:formats "application/transit+json" :encoder-opts]
          {:handlers writers}))))

(def transit-resp {:body (Point. 1 2)})

(deftest write-custom-transit
  (is (= "[\"~#Point\",[1,2]]"
         (slurp (:body (custom-transit-echo transit-resp)))))
  (is (= "[\"~#Point\",[1,2]]"
         (slurp (:body (custom-api-transit-echo (assoc transit-resp :headers {"accept" "application/transit+json"})))))))
