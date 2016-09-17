(ns muuntaja.interceptor
  (:require [muuntaja.core :as muuntaja]))

(defrecord Interceptor [name enter leave])

(defn format-interceptor [options]
  (let [formats (compile options)]
    (map->Interceptor
      {:name ::format
       :enter (fn [ctx]
                (update ctx :request (partial muuntaja/format-request formats)))
       :leave (fn [ctx]
                (update ctx :response (partial muuntaja/format-response formats (:request ctx))))})))
