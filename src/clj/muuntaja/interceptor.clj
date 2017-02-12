(ns muuntaja.interceptor
  (:require [muuntaja.core :as m]))

(defrecord Interceptor [name enter leave])

(defn format-interceptor [prototype]
  (let [formats (m/create prototype)]
    (map->Interceptor
      {:name ::format
       :enter #(update % :request (partial m/format-request formats))
       :leave #(update % :response (partial m/format-response formats (:request %)))})))
