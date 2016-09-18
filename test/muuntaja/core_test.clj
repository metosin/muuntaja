(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as muuntaja]
            [clojure.string :as str]))

(deftest core-test

  (testing "symmetic encode + decode for all formats"
    (let [muuntaja (muuntaja/compile muuntaja/default-options)
          data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (let [{:keys [encode decode]} (get-in muuntaja [:adapters format])]
          (= data (decode (encode data))))
        :json :edn :yaml :msgpack :transit-json :transit-msgpack)))

  (testing "adding new format"
    (let [format :upper
          upper-case-format {:decoder str/lower-case
                             :encoder str/upper-case}
          muuntaja (muuntaja/compile
                     (-> muuntaja/default-options
                         (assoc-in [:adapters format] upper-case-format)
                         (update :formats conj format)))
          {:keys [encode decode]} (get-in muuntaja [:adapters format])
          data "olipa kerran avaruus"]
      (is (= "OLIPA KERRAN AVARUUS" (encode data)))
      (is (= data (decode (encode data))))))

  (testing "non-existing format throws exception"
    (is (thrown?
          Exception
          (muuntaja/compile
            (-> muuntaja/default-options
                (update :formats conj :kikka))))))

  (testing "overriding adapter configs"
    (let [decode-json-kw (-> (muuntaja/compile
                               (-> muuntaja/default-options))
                             (get-in [:adapters :json :decode]))
          decode-json (-> (muuntaja/compile
                            (-> muuntaja/default-options
                                (muuntaja/with-decoder-opts :json {:keywords? false})))
                          (get-in [:adapters :json :decode]))]
      (is (= {:kikka true} (decode-json-kw "{\"kikka\":true}")))
      (is (= {"kikka" true} (decode-json "{\"kikka\":true}"))))))
