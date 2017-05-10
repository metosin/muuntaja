(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as str]
            [muuntaja.protocols :as protocols]
            [muuntaja.format.json :as json-format]
            [muuntaja.format.msgpack :as msgpack-format]
            [muuntaja.format.yaml :as yaml-format]
            [muuntaja.json :as json])
  (:import (java.nio.charset Charset)
           (java.io ByteArrayInputStream)))

(defn- to-byte-stream [x charset] (ByteArrayInputStream. (.getBytes x charset)))

(defn set-jvm-default-charset! [charset]
  (System/setProperty "file.encoding" charset)
  (doto
    (.getDeclaredField Charset "defaultCharset")
    (.setAccessible true)
    (.set nil nil))
  nil)

(defrecord Hello [^String name]
  json-format/EncodeJson
  (encode-json [_ charset]
    (to-byte-stream (json/to-json {"hello" name}) charset)))

(defmacro with-default-charset [charset & body]
  `(let [old-charset# (str (Charset/defaultCharset))]
     (try
       (set-jvm-default-charset! ~charset)
       ~@body
       (finally
         (set-jvm-default-charset! old-charset#)))))

(def m (m/create
         (-> m/default-options
             (msgpack-format/with-msgpack-format)
             (yaml-format/with-yaml-format))))

(deftest core-test
  (testing "encode & decode"
    (let [data {:kikka 42}]
      (is (= "{\"kikka\":42}" (slurp (m/encode m "application/json" data))))
      (is (= data (m/decode m "application/json" (m/encode m "application/json" data))))))

  (testing "symmetic encode + decode for all formats"
    (let [data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (= data (m/decode m format (m/encode m format data)))
        "application/json"
        "application/edn"
        "application/x-yaml"
        "application/msgpack"
        "application/transit+json"
        "application/transit+msgpack")))

  (testing "charsets"
    (testing "default is UTF-8"
      (is (= "UTF-8" (str (Charset/defaultCharset)))))
    (testing "default can be changed"
      (with-default-charset
        "UTF-16"
        (is (= "UTF-16" (str (Charset/defaultCharset)))))))

  (testing "on empty input"
    (let [empty (fn [] (ByteArrayInputStream. (byte-array 0)))
          m2 (m/create
               (-> m/default-options
                   (msgpack-format/with-msgpack-format)
                   (yaml-format/with-yaml-format)
                   (assoc :allow-empty-input? false)))]

      (testing "by default - nil is returned for empty stream"
        (is (nil? (m/decode m "application/transit+json" (empty)))))

      (testing "by default - nil input returns nil stream"
        (is (nil? (m/decode m "application/transit+json" nil))))

      (testing "optionally decoder can decide to throw"
        (is (thrown? Exception (m/decode m2 "application/transit+json" (empty))))
        (is (thrown? Exception (m/decode m2 "application/transit+json" nil))))

      (testing "all formats"
        (testing "with :allow-empty-input? false"

          (testing "json & yaml return nil"
            (are [format]
              (= nil (m/decode m2 format (empty)))
              "application/json"
              "application/x-yaml"))

          (testing "others fail"
            (are [format]
              (thrown-with-msg? Exception #"Malformed" (m/decode m2 format (empty)))
              "application/edn"
              "application/msgpack"
              "application/transit+json"
              "application/transit+msgpack")))

        (testing "with defaults"
          (testing "all formats return nil"
            (are [format]
              (= nil (m/decode m format (empty)))
              "application/json"
              "application/edn"
              "application/x-yaml"
              "application/msgpack"
              "application/transit+json"
              "application/transit+msgpack"))))))

  (testing "non-binary-formats encoding with charsets"
    (let [m (assoc m :charsets m/available-charsets)
          data {:fée "böz"}
          iso-encoded #(slurp (m/encode m % data "ISO-8859-1"))]
      (testing "application/json & application/edn use the given charset"
        (is (= "{\"f�e\":\"b�z\"}" (iso-encoded "application/json")))
        (is (= "{:f�e \"b�z\"}" (iso-encoded "application/edn"))))

      (testing "application/x-yaml & application/transit+json use the platform charset"
        (testing "utf-8"
          (is (= "{fée: böz}\n" (iso-encoded "application/x-yaml")))
          (is (= "[\"^ \",\"~:fée\",\"böz\"]" (iso-encoded "application/transit+json"))))
        (testing "when default charset is ISO-8859-1"
          (with-default-charset
            "ISO-8859-1"
            (testing "application/x-yaml works"
              (is (= "{f�e: b�z}\n" (iso-encoded "application/x-yaml"))))
            (testing "application/transit IS BROKEN"
              (is (not= "[\"^ \",\"~:f�e\",\"b�z\"]" (iso-encoded "application/transit+json")))))))))

  (testing "all formats handle different charsets symmetrically"
    (let [m (assoc m :charsets m/available-charsets)
          data {:fée "böz"}
          encode-decode #(as-> data $
                               (m/encode m % $ "ISO-8859-1")
                               (m/decode m % $ "ISO-8859-1"))]
      (are [format]
        (= data (encode-decode format))
        "application/json"
        "application/edn"
        ;; platform charset
        "application/x-yaml"
        ;; binary
        "application/msgpack"
        ;; platform charset
        "application/transit+json"
        ;; binary
        "application/transit+msgpack")))

  (testing "encoder & decoder"
    (let [m (m/create)
          data {:kikka 42}
          json-encoder (m/encoder m "application/json")
          json-decoder (m/decoder m "application/json")]
      (is (= "{\"kikka\":42}" (slurp (json-encoder data))))
      (is (= data (-> data json-encoder json-decoder)))

      (testing "invalid encoder /decoder returns nil"
        (is (nil? (m/encoder m "application/INVALID")))
        (is (nil? (m/decoder m "application/INVALID"))))

      (testing "decode exception"
        (is (thrown?
              Exception
              (json-decoder "{:invalid :syntax}"))))))

  (testing "encode-protocol"
    (let [encoder (m/encoder (m/create) "application/json")]
      (is (= "{\"hello\":\"Nekala\"}" (slurp (encoder (->Hello "Nekala") "utf-8"))))
      (is (= "{\"hello\":\"Nekala\"}" (slurp (encoder (->Hello "Nekala") "utf-16") :encoding "UTF-16")))))

  (testing "adding new format"
    (let [format "application/upper"
          upper-case-format {:decoder (fn [s _] (str/lower-case (slurp s)))
                             :encoder (fn [s _] (protocols/as-input-stream (str/upper-case s)))}
          m (m/create
              (-> m/default-options
                  (assoc-in [:formats format] upper-case-format)))
          {:keys [encode decode]} (get-in m [:adapters format])
          data "olipa kerran avaruus"]
      (is (= "OLIPA KERRAN AVARUUS" (slurp (encode data))))
      (is (= data (decode (encode data))))))

  (testing "setting non-existing format as default throws exception"
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (assoc :default-format "kikka"))))))

  (testing "selecting non-existing format as default throws exception"
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (m/select-formats ["kikka"]))))))

  (testing "overriding adapter options"
    (let [decode-json-kw (m/decoder
                           (m/create)
                           "application/json")
          decode-json (m/decoder
                        (m/create
                          (assoc-in
                            m/default-options
                            [:formats "application/json" :decoder-opts]
                            {:key-fn false}))
                        "application/json")]
      (is (= {:kikka true} (decode-json-kw "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json "{\"kikka\":true}")))))

  (testing "overriding invalid adapter options fails"
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (assoc-in
                  [:formats "application/jsonz" :encoder-opts]
                  {:keywords? false})))))
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (assoc-in
                  [:formats "application/jsonz" :decoder-opts]
                  {:keywords? false})))))))
