(ns muuntaja.json
  "JSON encoding and decoding based on Jackson Databind.

  Encoding example:

    (require '[muuntaja.json :as json])
    (json/to-json {:hello 1})
    ;; => \"{\\\"hello\\\":1}\"

  Decoding example:

    (def +data+ (json/to-json {:foo \"bar\"}))
    (json/from-json +data+)
    ;; => {\"foo\" \"bar\"}

  CONFIGURATION

  You can configure encoding and decoding by creating a custom mapper object
  with muuntaja.json/make-mapper. The options are passed in as a map.

  For example, to convert map keys into keywords while decoding:

    (json/from-json +data+ (json/make-mapper {:keywordize? true}))
    ;; => {:foo \"bar\"}

  See the docstring of muuntaja.json/make-mapper for all available options.

  CUSTOM ENCODERS

  Custom encoder is a function that take a value and a JsonGenerator object as
  the parameters. The function should call JsonGenerator methods to emit the
  desired JSON. This is the same as how custom encoders work in Cheshire.

  Custom encoders are configured by the make-mapper option :encoders, which is a
  map from types to encoder functions.

  For example, to encode java.awt.Color:

     (let [encoders {java.awt.Color (fn [color gen] (.writeString gen (str color)))}
           mapper (json/make-mapper {:encoders encoders})]
       (json/to-json (java.awt.Color. 1 2 3) mapper))
     ;; => \"\\\"java.awt.Color[r=1,g=2,b=3]\\\"\"

  MUUNTAJA.JSON VS CHESHIRE

  muuntaja.json uses Jackson Databind while Cheshire uses Jackson Core. In our
  benchmarks, muuntaja.json performs better than Cheshire (take look at
  json_perf_test.clj). On the other hand, Cheshire has a wider set of features
  and has been used in production much more."
  (:import
    com.fasterxml.jackson.databind.ObjectMapper
    com.fasterxml.jackson.databind.module.SimpleModule
    com.fasstringterxml.jackson.databind.SerializationFeature
    (muuntaja.jackson
      DateSerializer
      FunctionalSerializer
      KeywordSerializer
      KeywordKeyDeserializer
      PersistentHashMapDeserializer
      PersistentVectorDeserializer
      SymbolSerializer
      RatioSerializer)
    (java.io InputStream Writer)))

(set! *warn-on-reflection* true)

(defn- make-clojure-module
  "Create a Jackson Databind module to support Clojure datastructures.

  See make-mapper docstring for the documentation of the options."
  [{:keys [keywordize? pretty? encoders date-format]}]
  (doto (SimpleModule. "Clojure")
    (.addDeserializer java.util.List (PersistentVectorDeserializer.))
    (.addDeserializer java.util.Map (PersistentHashMapDeserializer.))
    (.addSerializer clojure.lang.Keyword (KeywordSerializer. false))
    (.addSerializer clojure.lang.Ratio (RatioSerializer.))
    (.addSerializer clojure.lang.Symbol (SymbolSerializer.))
    (.addKeySerializer clojure.lang.Keyword (KeywordSerializer. true))
    (.addSerializer java.util.Date (if date-format
                                     (DateSerializer. date-format)
                                     (DateSerializer.)))
    (as-> module
        (doseq [[cls encoder-fn] encoders]
          (.addSerializer module cls (FunctionalSerializer. encoder-fn))))
    (cond->
        ;; This key deserializer decodes the map keys into Clojure keywords.
        keywordize? (.addKeyDeserializer Object (KeywordKeyDeserializer.)))))

;mapper.enable(SerializationFeature.INDENT_OUTPUT)

(defn ^ObjectMapper make-mapper
  "Create an ObjectMapper with Clojure support.

  The optional first parameter is a map of options. The following options are
  available:

  :encoders     --  a map of custom encoders where keys should be types and values
                    should be encoder functions
  :keywordize?  --  set to true to convert map keys into keywords (default: false)

  Encoder functions take two parameters: the value to be encoded and a
  JsonGenerator object. The function should call JsonGenerator methods to emit
  the desired JSON."
  ([] (make-mapper {}))
  ([options]
   (doto (ObjectMapper.)
     (.registerModule (make-clojure-module options))
     (cond-> (:pretty options) (.enable (SerializationFeature/INDENT_OUTPUT))))))

(def ^ObjectMapper +default-mapper+
  "The default ObjectMapper instance used by muuntaja.json/to-json and
  muuntaja.json/from-json unless you pass in a custom one."
  (make-mapper {}))

(defn from-json
  "Decode a value from a JSON string or InputStream.

  To configure, pass in an ObjectMapper created with make-mapper."
  ([data] (from-json data +default-mapper+))
  ([data ^ObjectMapper mapper]
   (if (string? data)
     (.readValue mapper ^String data ^Class Object)
     (.readValue mapper ^InputStream data ^Class Object))))

(defn from-json-with-opts
  "Decode a value from a JSON string or InputStream.

  To configure, pass in an ObjectMapper created with make-mapper."
  ([data] (from-json data {}))
  ([data opts]
   (if (string? data)
     (.readValue (make-mapper opts) ^String data ^Class Object)
     (.readValue (make-mapper opts) ^InputStream data ^Class Object))))

(defn ^String to-json
  "Encode a value as a JSON string.

  To configure, pass in an ObjectMapper created with make-mapper."
  ([object] (to-json object +default-mapper+))
  ([object ^ObjectMapper mapper] (.writeValueAsString mapper object)))

(defn ^String to-json-with-opts
  "Encode a value as a JSON string.

  The optional second parameter is a map of options:
  :encoders     --  a map of custom encoders where keys should be types and values
                    should be encoder functions
  :keywordize?  --  set to true to convert map keys into keywords (default: false)
  :pretty      --  if set to true will use Jacksons PrettyPrinter default options to indent JSON"
  ([object] (to-json-with-opts object {}))
  ([object opts] (.writeValueAsString (make-mapper opts) object)))

(defn ^String write-to
  ([object ^Writer writer]
   (write-to object writer +default-mapper+))
  ([object ^Writer writer ^ObjectMapper mapper]
   (.writeValue mapper writer object)))
