(ns muuntaja.interceptor
  (:require [muuntaja.core :as m]))

(defrecord Interceptor [name enter leave])

(defn format-interceptor [prototype]
  (let [formats (m/create prototype)]
    (map->Interceptor
      {:name ::format
       :enter (fn [ctx]
                (update ctx :request (partial m/format-request formats)))
       :leave (fn [ctx]
                (update ctx :response (partial m/format-response formats (:request ctx))))})))
