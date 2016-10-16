(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as str]
            [muuntaja.protocols :as protocols]))

(deftest core-test

  (testing "symmetic encode + decode for all formats"
    (let [m (m/create m/default-options)
          data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (= data (m/decode m format (m/encode m format data)))
        "application/json"
        "application/edn"
        "application/x-yaml"
        "application/msgpack"
        "application/transit+json"
        "application/transit+msgpack")))

  (testing "encode & decode"
    (let [m (m/create m/default-options)
          data {:kikka 42}]
      (is (= "{\"kikka\":42}" (slurp (m/encode m "application/json" data))))
      (is (= data (m/decode m "application/json" (m/encode m "application/json" data))))))

  (testing "encoder & decoder"
    (let [m (m/create m/default-options)
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
                (m/with-formats ["kikka"]))))))

  (testing "overriding adapter options"
    (let [decode-json-kw (m/decoder
                           (m/create)
                           "application/json")
          decode-json (m/decoder
                        (m/create
                          (m/with-decoder-opts
                            m/default-options
                            "application/json"
                            {:keywords? false}))
                        "application/json")]
      (is (= {:kikka true} (decode-json-kw "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json "{\"kikka\":true}")))))

  (testing "overriding invalid adapter options fails"
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (m/with-decoder-opts "application/jsonz" {:keywords? false})))))
    (is (thrown?
          Exception
          (m/create
            (-> m/default-options
                (m/with-encoder-opts "application/jsonz" {:keywords? false})))))))
