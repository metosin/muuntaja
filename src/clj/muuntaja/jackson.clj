(ns muuntaja.jackson
  "Experimental Jackson-based JSON encoding/decoding."
  (:import
   com.fasterxml.jackson.databind.ObjectMapper
   com.fasterxml.jackson.databind.module.SimpleModule
   (muuntaja.jackson
    KeywordSerializer
    KeywordKeyDeserializer
    PersistentHashMapDeserializer
    PersistentVectorDeserializer)))

(set! *warn-on-reflection* true)

(defn make-clojure-module []
  (doto (SimpleModule. "Clojure")
    (.addDeserializer java.util.List (PersistentVectorDeserializer.))
    (.addDeserializer java.util.Map (PersistentHashMapDeserializer.))
    (.addSerializer clojure.lang.Keyword (KeywordSerializer. false))
    (.addKeySerializer clojure.lang.Keyword (KeywordSerializer. true))
    ;; This key deserializer decodes the map keys into Clojure keywords.
    #_(.addKeyDeserializer Object (KeywordKeyDeserializer.))))

(defn ^ObjectMapper make-mapper []
  (doto (ObjectMapper.)
    (.registerModule (make-clojure-module))))

(def ^ObjectMapper +mapper+ (make-mapper))

(defn from-json [^String data] (.readValue +mapper+ data Object))
(defn to-json [object] (.writeValueAsString +mapper+ object))
