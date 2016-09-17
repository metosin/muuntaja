(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as muuntaja]))

(deftest core-test
  (testing "symmetic encode + decode for all formats"
    (let [muuntaja (muuntaja/compile muuntaja/default-options)
          data {:kikka 42, :childs {:facts [1.2 true {:so "nested"}]}}]
      (are [format]
        (let [{:keys [encode decode]} (get-in muuntaja [:adapters format])]
          (= data (decode (encode data))))
        :json :edn :yaml :msgpack :transit-json :transit-msgpack))))
