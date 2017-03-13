(ns muuntaja.interceptor
  (:require [muuntaja.core :as m]))

(defn format
  "Interceptor that negotiates a request body based on accept, accept-charset
  and content-type headers and decodes the body with an attached Muuntaja
  instance into `:body-params`. Encodes also the response body with the same
  Muuntaja instance based on the negotiation information or override information
  provided by the handler.

  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [formats (m/create prototype)]
    {:name ::format
     :enter #(update % :request (partial m/format-request formats))
     :leave #(update % :response (partial m/format-response formats (:request %)))}))

(defn format-negotiate
  "Interceptor that negotiates a request body based on accept, accept-charset
  and content-type headers with an attached Muuntaja instance. Injects negotiation
  results into request for `format-request` interceptor to use.

  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [formats (m/create prototype)]
    {:name ::format
     :enter #(update % :request (partial m/negotiate-ring-request formats))}))

(defn format-request
  "Interceptor that decodes the request body with an attached Muuntaja
  instance into `:body-params` based on the negotiation information provided
  by `format-negotiate` interceptor.

  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [formats (m/create prototype)]
    {:name ::format
     :enter #(update % :request (partial m/format-request formats))}))

(defn format-response
  "Interceptor that encodes also the response body with the attached
  Muuntaja instance, based on request negotiation information provided by
  `format-negotiate` interceptor or override information provided by the handler.

  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [formats (m/create prototype)]
    {:name ::format
     :leave #(update % :response (partial m/format-response formats (:request %)))}))
