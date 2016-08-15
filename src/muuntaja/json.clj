(ns muuntaja.json ^:no-doc
  (:import [com.fasterxml.jackson.databind.node JsonNodeFactory ObjectNode ArrayNode]))

(def ^JsonNodeFactory factory (JsonNodeFactory/instance))

(defn ^ObjectNode object []
  (.objectNode factory))

(defn array ^ArrayNode []
  (.arrayNode ^JsonNodeFactory factory))
