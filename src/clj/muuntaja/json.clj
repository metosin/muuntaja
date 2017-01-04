(ns muuntaja.json
  "Jackson-based JSON encoding and decoding."
  (:import
    com.fasterxml.jackson.databind.ObjectMapper
    com.fasterxml.jackson.databind.ser.std.StdSerializer
    com.fasterxml.jackson.databind.module.SimpleModule
    (muuntaja.jackson
      DateSerializer
      FunctionalSerializer
      KeywordSerializer
      KeywordKeyDeserializer
      PersistentHashMapDeserializer
      PersistentVectorDeserializer
      SymbolSerializer
      RatioSerializer)
    (java.io InputStream)))

(set! *warn-on-reflection* true)

(defn make-clojure-module
  [{:keys [keywordize? encoders]}]
  (doto (SimpleModule. "Clojure")
    (.addDeserializer java.util.List (PersistentVectorDeserializer.))
    (.addDeserializer java.util.Map (PersistentHashMapDeserializer.))
    (.addSerializer clojure.lang.Keyword (KeywordSerializer. false))
    (.addSerializer clojure.lang.Ratio (RatioSerializer.))
    (.addSerializer clojure.lang.Symbol (SymbolSerializer.))
    (.addSerializer java.util.Date (DateSerializer.))
    (.addKeySerializer clojure.lang.Keyword (KeywordSerializer. true))
    (as-> module
        (doseq [[cls encoder-fn] encoders]
          (.addSerializer module cls (FunctionalSerializer. encoder-fn))))
    (cond->
      ;; This key deserializer decodes the map keys into Clojure keywords.
      keywordize? (.addKeyDeserializer Object (KeywordKeyDeserializer.)))))

(defn ^ObjectMapper make-mapper
  ([] (make-mapper {}))
  ([options]
   (doto (ObjectMapper.)
     (.registerModule (make-clojure-module options)))))

(def ^ObjectMapper +default-mapper+ (make-mapper))

(defn from-json
  ([data] (from-json data +default-mapper+))
  ([data ^ObjectMapper mapper]
   (if (string? data)
     (.readValue mapper ^String data ^Class Object)
     (.readValue mapper ^InputStream data ^Class Object))))

(defn ^String to-json
  ([object] (to-json object +default-mapper+))
  ([object ^ObjectMapper mapper] (.writeValueAsString mapper object)))
