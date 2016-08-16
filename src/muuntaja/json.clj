(ns muuntaja.json
  {:doc "experimental json encoding & decoding, not for prod usage", :no-doc true}
  (:import [com.fasterxml.jackson.databind.node JsonNodeFactory ObjectNode ArrayNode]
           [com.fasterxml.jackson.databind ObjectMapper]
           [java.util LinkedHashMap$Entry LinkedHashMap ArrayList]
           [java.io InputStream]))

(def ^JsonNodeFactory factory (JsonNodeFactory/instance))
(def ^ObjectMapper mapper (ObjectMapper.))

(defn ^ObjectNode object []
  (.objectNode factory))

(defn array ^ArrayNode []
  (.arrayNode ^JsonNodeFactory factory))

(declare clojurify)

(defn- mapify [^LinkedHashMap data]
  (let [acc (transient {})
        iter (.iterator (.entrySet data))]
    (loop []
      (if (.hasNext iter)
        (let [entry ^LinkedHashMap$Entry (.next iter)]
          (assoc! acc (.getKey entry) (clojurify (.getValue entry)))
          (recur))
        (persistent! acc)))))

(defn- vectorify [^Iterable data]
  (let [acc (transient [])
        iter (.iterator data)]
    (loop []
      (if (.hasNext iter)
        (let [object (.next iter)]
          (conj! acc (clojurify object))
          (recur))
        (persistent! acc)))))

(defn- clojurify [data]
  (cond
    (instance? LinkedHashMap data) (mapify data)
    (instance? ArrayList data) (vectorify data)
    :else data))

(defn decode-map [x]
  (clojurify
    (if (string? x)
      (.readValue mapper ^String x LinkedHashMap)
      (.readValue mapper ^InputStream x LinkedHashMap))))
