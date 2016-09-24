(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as muuntaja]
            [clojure.string :as str]))

(deftest core-test

  (testing "symmetic encode + decode for all formats"
    (let [m (muuntaja/create muuntaja/default-options)
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
    (let [m (muuntaja/create muuntaja/default-options)
          data {:kikka 42}]
      (is (= "{\"kikka\":42}" (muuntaja/encode m "application/json" data)))
      (is (= data (muuntaja/decode m "application/json" (muuntaja/encode m "application/json" data))))))

  (testing "adding new format"
    (let [format "application/upper"
          upper-case-format {:decoder str/lower-case
                             :encoder str/upper-case}
          m (muuntaja/create
              (-> muuntaja/default-options
                  (assoc-in [:formats format] upper-case-format)))
          {:keys [encode decode]} (get-in m [:adapters format])
          data "olipa kerran avaruus"]
      (is (= "OLIPA KERRAN AVARUUS" (encode data)))
      (is (= data (decode (encode data))))))

  (testing "setting non-existing format as default throws exception"
    (is (thrown?
          Exception
          (muuntaja/create
            (-> muuntaja/default-options
                (assoc :default-format "kikka"))))))

  (testing "selecting non-existing format as default throws exception"
    (is (thrown?
          Exception
          (muuntaja/create
            (-> muuntaja/default-options
                (muuntaja/with-formats ["kikka"]))))))

  (testing "overriding adapter options"
    (let [decode-json-kw (-> (muuntaja/create
                               (-> muuntaja/default-options))
                             (get-in [:adapters "application/json" :decode]))
          decode-json (-> (muuntaja/create
                            (-> muuntaja/default-options
                                (muuntaja/with-decoder-opts "application/json" {:keywords? false})))
                          (get-in [:adapters "application/json" :decode]))]
      (is (= {:kikka true} (decode-json-kw "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json "{\"kikka\":true}")))))

  (testing "overriding invalid adapter options fails"
    (is (thrown?
          Exception
          (muuntaja/create
            (-> muuntaja/default-options
                (muuntaja/with-decoder-opts "application/jsonz" {:keywords? false})))))
    (is (thrown?
          Exception
          (muuntaja/create
            (-> muuntaja/default-options
                (muuntaja/with-encoder-opts "application/jsonz" {:keywords? false})))))))
