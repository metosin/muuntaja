(ns ring.middleware.format-params-test
  (:use [clojure.test]
        [ring.middleware.format-params])
  (:require [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [clojure.walk :refer [stringify-keys keywordize-keys]]
            [msgpack.core :as msgpack]
            [clojure.string :as string])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn stream [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

(def json-echo
  (wrap-restful-params identity {:formats [:json]}))

;; stolen from ring-json-params to confirm compatibility

(deftest noop-with-other-content-type
  (let [req {:content-type "application/xml"
             :body (stream "<xml></xml>")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= "<xml></xml>" (slurp (:body resp))))
    (is (= {"id" 3} (:params resp)))
    (is (nil? (:json-params resp)))))

(deftest augments-with-json-content-type
  (let [req {:content-type "application/json; charset=UTF-8"
             :body (stream "{\"foo\": \"bar\"}")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(deftest augments-with-vnd-json-content-type
  (let [req {:content-type "application/vnd.foobar+json; charset=UTF-8"
             :body (stream "{\"foo\": \"bar\"}")
             :params {"id" 3}}
        resp (json-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(defn key-fn [s]
  (-> s (string/replace #"_" "-") keyword))

(deftest json-options-test
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-restful-params identity {:formats [:json-kw]
                                                       :format-options {:json-kw {:key-fn key-fn}}})
                         {:content-type "application/json"
                          :body (stream "{\"foo_bar\":\"bar\"}")}))))
  (is (= {:foo-bar "bar"}
         (:body-params ((wrap-restful-params identity {:format-options {:json {:key-fn key-fn}}})
                         {:content-type "application/json"
                          :body (stream "{\"foo_bar\":\"bar\"}")})))))

(def yaml-echo
  (wrap-restful-params identity {:formats [:yaml]}))

(deftest augments-with-yaml-content-type
  (let [req {:content-type "application/x-yaml; charset=UTF-8"
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp (yaml-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def yaml-kw-echo
  (wrap-restful-params identity {:formats [:yaml-kw]}))

(deftest augments-with-yaml-kw-content-type
  (let [req {:content-type "application/x-yaml; charset=UTF-8"
             :body (stream "foo: bar")
             :params {"id" 3}}
        resp (yaml-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def msgpack-echo
  (wrap-restful-params identity {:formats [:msgpack]}))

(deftest augments-with-msgpack-content-type
  (let [req {:content-type "application/msgpack"
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-echo req)]
    (is (= {"id" 3 "foo" "bar"} (:params resp)))
    (is (= {"foo" "bar"} (:body-params resp)))))

(def msgpack-kw-echo
  (wrap-restful-params identity {:formats [:msgpack-kw]}))

(deftest augments-with-msgpack-kw-content-type
  (let [req {:content-type "application/msgpack"
             :body (ByteArrayInputStream. (msgpack/pack (stringify-keys {:foo "bar"})))
             :params {"id" 3}}
        resp (msgpack-kw-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def clojure-echo
  (wrap-restful-params identity {:formats [:edn]}))

(deftest augments-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))


(deftest augments-with-clojure-content-prohibit-eval-in-reader
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo #=(java.util.Date.)}")
             :params {"id" 3}}]
    (try
      (clojure-echo req)
      (is false "Eval in reader permits arbitrary code execution.")
      (catch Exception ignored))))

(deftest no-body-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3} (:params resp)))
    (is (= nil (:body-params resp)))))

(deftest whitespace-body-with-clojure-content-type
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "\t  ")
             :params {"id" 3}}
        resp (clojure-echo req)]
    (is (= {"id" 3} (:params resp)))
    (is (= nil (:body-params resp)))))

;;;;;;;;;;;;;
;; Transit ;;
;;;;;;;;;;;;;

(defn stream-transit
  [fmt data]
  (let [out (ByteArrayOutputStream.)
        wrt (transit/writer out fmt)]
    (transit/write wrt data)
    (io/input-stream (.toByteArray out))))

(def transit-json-echo
  (wrap-restful-params identity {:formats [:transit-json]}))

(deftest augments-with-transit-json-content-type
  (let [req {:content-type "application/transit+json"
             :body (stream-transit :json {:foo "bar"})
             :params {"id" 3}}
        resp (transit-json-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

(def transit-msgpack-echo
  (wrap-restful-params identity {:formats [:transit-msgpack]}))

(deftest augments-with-transit-msgpack-content-type
  (let [req {:content-type "application/transit+msgpack"
             :body (stream-transit :msgpack {:foo "bar"})
             :params {"id" 3}}
        resp (transit-msgpack-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))))

;;;;;;;;;;;;;;;;;;;;
;; Restful Params ;;
;;;;;;;;;;;;;;;;;;;;

(def restful-echo
  (wrap-restful-params identity))

(def safe-restful-echo
  (wrap-restful-params identity
                       {:handle-error (fn [_ _ _] {:status 500})}))

(deftest test-restful-params-wrapper
  (let [req {:content-type "application/clojure; charset=UTF-8"
             :body (stream "{:foo \"bar\"}")
             :params {"id" 3}}
        resp (restful-echo req)]
    (is (= {"id" 3 :foo "bar"} (:params resp)))
    (is (= {:foo "bar"} (:body-params resp)))
    (is (= 500 (get (safe-restful-echo (assoc req :body (stream "{:foo \"bar}"))) :status)))))

(defn stream-iso [s]
  (ByteArrayInputStream. (.getBytes s "ISO-8859-1")))

(deftest test-different-params-charset
  (let [req {:content-type "application/clojure; charset=ISO-8859-1"
             :body (stream-iso "{:fée \"böz\"}")
             :params {"id" 3}}
        resp (restful-echo req)]
    (is (= {"id" 3 :fée "böz"} (:params resp)))
    (is (= {:fée "böz"} (:body-params resp)))))

(deftest test-list-body-request
  (let [req {:content-type "application/json"
             :body (ByteArrayInputStream.
                     (.getBytes "[\"gregor\", \"samsa\"]"))}]
    ((wrap-restful-params
       (fn [{:keys [body-params]}] (is (= ["gregor" "samsa"] body-params)))
       {:formats [:json]})
      req)))

(deftest test-optional-body
  ((wrap-restful-params
     (fn [request]
       (is (nil? (:body request))))
     {:formats [:json]})
    {:body nil}))

(deftest test-custom-handle-error
  (are [format content-type body]
    (let [req {:body body
               :content-type content-type}
          resp ((wrap-restful-params
                  identity
                  {:formats [format]
                   :handle-error (constantly {:status 999})})
                 req)]
      (= 999 (:status resp)))
    :json "application/json" "{:a 1}"
    :edn "application/edn" "{\"a\": 1}"))

;;
;; Transit options
;;

(defrecord Point [x y])

(def readers
  {"Point" (transit/read-handler (fn [[x y]] (Point. x y)))})

(def custom-restful-transit-json-echo
  (wrap-restful-params identity {:format-options {:transit-json {:handlers readers}}}))

(def transit-body "[\"^ \", \"~:p\", [\"~#Point\",[1,2]]]")

(deftest read-custom-transit
  (testing "wrap-restful-params, transit options"
    (let [req (custom-restful-transit-json-echo {:content-type "application/transit+json"
                                                 :body (stream transit-body)})]
      (is (= {:p (Point. 1 2)}
             (:params req)
             (:body-params req))))))
