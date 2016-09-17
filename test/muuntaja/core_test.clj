(ns muuntaja.core-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as muuntaja]))

(deftest core-test
  (testing "symmetic encode + decode for all formats"
    (let [muuntaja (muuntaja/compile muuntaja/default-options)]
      (are [format]
        (let [{:keys [encode decode]} (get-in muuntaja [:adapters format])]
          (= {:kikka 42} (decode (encode {:kikka 42}))))
        :json :edn :yaml :msgpack :transit-json :transit-msgpack))))
