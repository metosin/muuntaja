(ns muuntaja.interceptor
  (:refer-clojure :exclude [format])
  (:require [muuntaja.core :as m]))

(defn format
  "Interceptor that negotiates a request body based on accept, accept-charset
  and content-type headers and decodes the body with an attached Muuntaja
  instance into `:body-params`. Encodes also the response body with the same
  Muuntaja instance based on the negotiation information or override information
  provided by the handler.

  Takes a pre-configured Muuntaja or options maps an argument.
  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [m (m/create prototype)]
    {:name ::format
     :enter #(update % :request (partial m/format-request m))
     :leave #(update % :response (partial m/format-response m (:request %)))}))

(defn format-negotiate
  "Interceptor that negotiates a request body based on accept, accept-charset
  and content-type headers with an attached Muuntaja instance. Injects negotiation
  results into request for `format-request` interceptor to use.

  Takes a pre-configured Muuntaja or options maps an argument.
  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [m (m/create prototype)]
    {:name ::format-negotiate
     :enter #(update % :request (partial m/negotiate-request m))}))

(defn format-request
  "Interceptor that decodes the request body with an attached Muuntaja
  instance into `:body-params` based on the negotiation information provided
  by `format-negotiate` interceptor.

  Takes a pre-configured Muuntaja or options maps an argument.
  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [m (m/create prototype)]
    {:name ::format-request
     :enter #(update % :request (partial m/format-request m))}))

(defn format-response
  "Interceptor that encodes also the response body with the attached
  Muuntaja instance, based on request negotiation information provided by
  `format-negotiate` interceptor or override information provided by the handler.

  Takes a pre-configured Muuntaja or options maps an argument.
  See https://github.com/metosin/muuntaja for all options and defaults."
  [prototype]
  (let [m (m/create prototype)]
    {:name ::format-response
     :leave #(update % :response (partial m/format-response m (:request %)))}))
