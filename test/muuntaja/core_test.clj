(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [clojure.string :as str]))

(deftest core-test

  (testing "symmetic encode + decode for all formats"
    (let [m (m/create m/default-options)
          data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (let [{:keys [encode decode]} (get-in m [:adapters format])]
          (= data (decode (encode data))))
        "application/json"
        "application/edn"
        "application/x-yaml"
        "application/msgpack"
        "application/transit+json"
        "application/transit+msgpack")))

  (testing "encode & decode"
    (let [m (m/create m/default-options)
          data {:kikka 42}]
      (is (= "{\"kikka\":42}" (m/encode m "application/json" data)))
      (is (= data (m/decode m "application/json" (m/encode m "application/json" data))))))

  (testing "encoder & decoder"
    (let [m (m/create m/default-options)
          data {:kikka 42}
          json-encoder (m/encoder m "application/json")
          json-decoder (m/decoder m "application/json")]
      (is (= "{\"kikka\":42}" (json-encoder data)))
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
          upper-case-format {:decoder str/lower-case
                             :encoder str/upper-case}
          m (m/create
              (-> m/default-options
                  (assoc-in [:formats format] upper-case-format)))
          {:keys [encode decode]} (get-in m [:adapters format])
          data "olipa kerran avaruus"]
      (is (= "OLIPA KERRAN AVARUUS" (encode data)))
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
    (let [decode-json-kw (-> (m/create
                               (-> m/default-options))
                             (get-in [:adapters "application/json" :decode]))
          decode-json (-> (m/create
                            (-> m/default-options
                                (m/with-decoder-opts "application/json" {:keywords? false})))
                          (get-in [:adapters "application/json" :decode]))]
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
