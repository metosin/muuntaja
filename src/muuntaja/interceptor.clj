(ns muuntaja.interceptor
  (:require [muuntaja.core :as m])
  (:import [muuntaja.core Formats]))

(defrecord Interceptor [name enter leave])

(defn format-interceptor [options-or-formats]
  (let [formats (if (instance? Formats options-or-formats)
                  options-or-formats
                  (m/create options-or-formats))]
    (map->Interceptor
      {:name ::format
       :enter (fn [ctx]
                (update ctx :request (partial m/format-request formats)))
       :leave (fn [ctx]
                (update ctx :response (partial m/format-response formats (:request ctx))))})))
