(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as str]
            [muuntaja.format.core :as core]
            [muuntaja.format.form :as form-format]
            [muuntaja.format.cheshire :as cheshire-format]
            [muuntaja.format.msgpack :as msgpack-format]
            [muuntaja.format.yaml :as yaml-format]
            [jsonista.core :as j]
            [clojure.java.io :as io]
            [muuntaja.protocols :as protocols]
            [muuntaja.util :as util])
  (:import (java.nio.charset Charset)
           (java.io FileInputStream)
           (java.nio.file Files)))

(defn set-jvm-default-charset! [charset]
  (System/setProperty "file.encoding" charset)
  (doto
    (.getDeclaredField Charset "defaultCharset")
    (.setAccessible true)
    (.set nil nil))
  nil)

(defmacro with-default-charset [charset & body]
  `(let [old-charset# (str (Charset/defaultCharset))]
     (try
       (set-jvm-default-charset! ~charset)
       ~@body
       (finally
         (set-jvm-default-charset! old-charset#)))))

(def m
  (m/create
    (-> m/default-options
        (m/install form-format/format)
        (m/install msgpack-format/format)
        (m/install yaml-format/format)
        (m/install cheshire-format/format "application/json+cheshire"))))

(deftest core-test
  (testing "muuntaja?"
    (is (m/muuntaja? m)))

  (testing "default encodes & decodes"
    (is (= #{"application/edn"
             "application/json"
             "application/transit+json"
             "application/transit+msgpack"}
           (m/encodes m/instance)
           (m/decodes m/instance))))

  (testing "custom encodes & decodes"
    (is (= #{"application/edn"
             "application/json"
             "application/json+cheshire"
             "application/msgpack"
             "application/transit+json"
             "application/transit+msgpack"
             "application/x-www-form-urlencoded"
             "application/x-yaml"}
           (m/encodes m)
           (m/decodes m))))

  (testing "encode & decode"
    (let [data {:kikka 42}]
      (testing "with default instance"
        (is (= "{\"kikka\":42}" (slurp (m/encode "application/json" data))))
        (is (= data (m/decode "application/json" (m/encode "application/json" data)))))
      (testing "with muuntaja instance"
        (is (= "{\"kikka\":42}" (slurp (m/encode m "application/json" data))))
        (is (= data (m/decode m "application/json" (m/encode m "application/json" data)))))))

  (testing "symmetic encode + decode for all formats"
    (let [data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (= data (m/decode m format (m/encode m format data)))
        "application/json"
        "application/json+cheshire"
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
    (let [empty (fn [] (util/byte-stream (byte-array 0)))
          m2 (m/create
               (-> m/default-options
                   (m/install msgpack-format/format)
                   (m/install yaml-format/format)
                   (m/install cheshire-format/format "application/json+cheshire")
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

          (testing "cheshire json & yaml return nil"
            (are [format]
              (= nil (m/decode m2 format (empty)))
              "application/json+cheshire"
              "application/x-yaml"))

          (testing "others fail"
            (are [format]
              (thrown-with-msg? Exception #"Malformed" (m/decode m2 format (empty)))
              "application/edn"
              "application/json"
              "application/msgpack"
              "application/transit+json"
              "application/transit+msgpack")))

        (testing "with defaults"
          (testing "all formats return nil"
            (are [format]
              (= nil (m/decode m format (empty)))
              "application/json"
              "application/json+cheshire"
              "application/edn"
              "application/x-yaml"
              "application/msgpack"
              "application/transit+json"
              "application/transit+msgpack"))))))

  (testing "non-binary-formats encoding with charsets"
    (let [data {:fée "böz"}
          iso-encoded #(slurp (m/encode m % data "ISO-8859-1"))]
      (testing "application/json & application/edn use the given charset"
        (is (= "{\"f�e\":\"b�z\"}" (iso-encoded "application/json")))
        (is (= "{\"f�e\":\"b�z\"}" (iso-encoded "application/json+cheshire")))
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
    (let [data {:fée "böz"}
          encode-decode #(as-> data $
                               (m/encode m % $ "ISO-8859-1")
                               (m/decode m % $ "ISO-8859-1"))]
      (are [format]
        (= data (encode-decode format))
        "application/json"
        "application/json+cheshire"
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

  (testing "adding new format"
    (let [name "application/upper"
          upper-case-format {:name name
                             :decoder (reify
                                        core/Decode
                                        (decode [_ data _]
                                          (str/lower-case (slurp data))))
                             :encoder (reify
                                        core/EncodeToBytes
                                        (encode-to-bytes [_ data _]
                                          (.getBytes (str/upper-case data))))}
          m (m/create (m/install m/default-options upper-case-format))
          encode (m/encoder m name)
          decode (m/decoder m name)
          data "olipa kerran avaruus"]
      (is (= "OLIPA KERRAN AVARUUS" (slurp (encode data))))
      (is (= data (decode (encode data))))))

  (testing "invalid format fails fast"
    (let [upper-case-format {:name "application/upper"
                             :decoder (fn [_ data _]
                                        (str/lower-case (slurp data)))}]
      (is (thrown? Exception (m/create (m/install m/default-options upper-case-format))))))

  (testing "implementing wrong protocol fails fast"
    (let [upper-case-format {:name "application/upper"
                             :return :output-stream
                             :encoder (reify
                                        core/EncodeToBytes
                                        (encode-to-bytes [_ data _]
                                          (.getBytes (str/upper-case data))))}]
      (is (thrown? Exception (m/create (m/install m/default-options upper-case-format))))))

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
                            {:decode-key-fn false}))
                        "application/json")
          decode-json2 (m/decoder
                         (m/create
                           (assoc-in
                             m/default-options
                             [:formats "application/json" :opts]
                             {:decode-key-fn false}))
                         "application/json")
          decode-json3 (m/decoder
                         (m/create
                           (assoc-in
                             m/default-options
                             [:formats "application/json" :opts]
                             {:mapper (j/object-mapper {:decode-key-fn false})}))
                         "application/json")]
      (is (= {:kikka true} (decode-json-kw "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json2 "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json3 "{\"kikka\":true}")))))

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

(deftest form-data
  (testing "basic form encoding"
    (let [data {:kikka 42, :childs ['not "so" "nested"]}
          format "application/x-www-form-urlencoded"]
      (is (= "kikka=42&childs=not&childs=so&childs=nested"
             (slurp (m/encode m format data))))))

  (testing "basic form encoding with string keywords"
    (let [data {"kikka" 42, "childs" ['not "so" "nested"]}
          format "application/x-www-form-urlencoded"]
      (is (= "kikka=42&childs=not&childs=so&childs=nested"
             (slurp (m/encode m format data))))))

  (testing "basic form decoding"
    (let [data "kikka=42&childs=not&childs=so&childs=nested=but+messed+up"
          format "application/x-www-form-urlencoded"]
      (is (= {:kikka "42", :childs ["not" "so" "nested=but messed up"]}
             (m/decode m format data)))))

  (testing "form decoding with different decode-key-fn"
    (let [data "kikka=42&childs=not&childs=so&childs=nested=but+messed+up"
          format "application/x-www-form-urlencoded"]
      (is (= {"kikka" "42", "childs" ["not" "so" "nested=but messed up"]}
             (-> (m/options m)
                 (assoc-in [:formats "application/x-www-form-urlencoded" :decoder-opts :decode-key-fn] identity)
                 (m/create)
                 (m/decode format data)))))))

(deftest cheshire-json-options
  (testing "pre 0.6.0 options fail at creation time"
    (testing ":bigdecimals?"
      (is (thrown-with-msg?
            AssertionError
            #"default JSON formatter has changed"
            (m/create
              (-> m/default-options
                  (assoc-in
                    [:formats "application/json" :decoder-opts]
                    {:bigdecimals? false}))))))
    (testing ":key-fn"
      (is (thrown-with-msg?
            Error
            #"default JSON formatter has changed"
            (m/create
              (-> m/default-options
                  (assoc-in
                    [:formats "application/json" :decoder-opts]
                    {:key-fn false}))))))))

(deftest slurp-test
  (let [file (io/file "dev-resources/json10b.json")
        expected (slurp file)]
    (testing "bytes"
      (is (= expected (m/slurp (Files/readAllBytes (.toPath file))))))
    (testing "File"
      (is (= expected (m/slurp file))))
    (testing "InputStream"
      (is (= expected (m/slurp (FileInputStream. file)))))
    (testing "StreamableResponse"
      (is (= expected (m/slurp (protocols/->StreamableResponse (partial io/copy file))))))
    (testing "String"
      (is (= expected (m/slurp expected))))
    (testing "nil"
      (is (= nil (m/slurp nil))))))

(deftest encode-to-byte-stream-test
  (testing "symmetic encode + decode for all formats"
    (let [m (m/create
              (-> m/default-options
                  (assoc :return :output-stream)
                  (m/install form-format/format)
                  (m/install msgpack-format/format)
                  (m/install yaml-format/format)
                  (m/install cheshire-format/format "application/json+cheshire")))]
      (let [data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
        (are [format]
          (= data (m/decode m format (m/encode m format data)))
          "application/json"
          "application/json+cheshire"
          "application/edn"
          "application/x-yaml"
          "application/msgpack"
          "application/transit+json"
          "application/transit+msgpack")))))
