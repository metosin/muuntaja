(ns ring.middleware.format-params
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.tools.reader.edn :as edn]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack])
  (:import [com.ibm.icu.text CharsetDetector]
           [java.io ByteArrayInputStream InputStream ByteArrayOutputStream]
           [java.nio.charset Charset]))

(set! *warn-on-reflection* true)

(def available-charsets
  "Set of recognised charsets by the current JVM"
  (into #{} (map str/lower-case (.keySet (Charset/availableCharsets)))))

(defn ^:no-doc guess-charset
  [{:keys [#^bytes body]}]
  (try
    (let [#^CharsetDetector detector (CharsetDetector.)]
      (.enableInputFilter detector true)
      (.setText detector body)
      (let [m (.detect detector)
            encoding (.getName m)]
        (if (available-charsets encoding)
          encoding)))
    (catch Exception _ nil)))

(defn ^:no-doc get-charset
  [{:keys [content-type]}]
  (if content-type
    (second (re-find #";\s*charset=([^\s;]+)" content-type))))

(defn get-or-guess-charset
  "Tries to get the request encoding from the header or guess
  it if not given in *Content-Type*. Defaults to *utf-8*"
  [req]
  (or
    (get-charset req)
    (guess-charset req)
    "utf-8"))

(defn ^:no-doc get-or-default-charset
  [req]
  (or
    (get-charset req)
    "utf-8"))

(defn make-type-request-pred
  "Function that returns a predicate fn checking if *Content-Type*
   request header matches a specified regexp and body is set."
  [regexp]
  (fn [{:keys [body] :as req}]
    (if-let [#^String type (get req :content-type
                                (get-in req [:headers "Content-Type"]
                                        (get-in req [:headers "content-type"])))]
      (and body (not (empty? (re-find regexp type)))))))

(defn ^:no-doc slurp-to-bytes
  #^bytes
  [#^InputStream in]
  (if in
    (let [buf (byte-array 4096)
          out (ByteArrayOutputStream.)]
      (loop []
        (let [r (.read in buf)]
          (when (not= r -1)
            (.write out buf 0 r)
            (recur))))
      (.toByteArray out))))

(defn default-handle-error
  "Default error handling function used, which rethrows the Exception"
  [e _ _]
  (throw e))

(defn wrap-format-params
  "Wraps a handler such that requests body are deserialized from to
   the right format, added in a *:body-params* key and merged in *:params*.
   It takes 4 args:

 + **:predicate** is a predicate taking the request as sole argument to
                  test if deserialization should be used.
 + **:decoder** specifies a fn taking the body String as sole argument and
                giving back a hash-map.
 + **:charset** can be either a string representing a valid charset or a fn
                taking the req as argument and returning a valid charset.
 + **:binary?** if true *:charset* will be ignored and decoder will receive
               an *InputStream*
 + **:handle-error** is a fn with a sig [exception handler request].
                     Return (handler obj) to continue executing a modified
                     request or directly a map to answer immediately. Defaults
                     to just rethrowing the Exception"
  [handler {:keys [predicate decoder charset handle-error binary?]}]
  (let [charset (or charset get-or-guess-charset)
        handle-error (or handle-error default-handle-error)]
    (fn [{:keys [#^InputStream body] :as req}]
      (try
        (if (and body (predicate req))
          (let [byts (slurp-to-bytes body)]
            (if (> (count byts) 0)
              (let [fmt-params (if binary?
                                 (decoder (ByteArrayInputStream. byts))
                                 (let [#^String char-enc (if (string? charset)
                                                           charset
                                                           (charset (assoc req :body byts)))
                                       bstr (String. byts char-enc)]
                                   (decoder bstr)))
                    req* (assoc req
                           :body-params fmt-params
                           :params (merge (:params req)
                                          (when (map? fmt-params) fmt-params))
                           :body (ByteArrayInputStream. byts))]
                (handler req*))
              (handler req)))
          (handler req))
        (catch Exception e
          (handle-error e handler req))))))

(def ^:no-doc json-request?
  (make-type-request-pred #"^application/(vnd.+)?json"))

(defn make-json-decoder [{:keys [key-fn array-coerce-fn] :as o}]
  (cond
    array-coerce-fn (fn [s] (json/parse-string s key-fn array-coerce-fn))
    key-fn (fn [s] (json/parse-string s key-fn))
    :else (fn [s] (json/parse-string s))))

(def ^:no-doc msgpack-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?msgpack"))

(defn decode-msgpack [body]
  (with-open [i (clojure.java.io/input-stream (slurp-to-bytes body))]
    (let [data-input (java.io.DataInputStream. i)]
      (msgpack/unpack-stream data-input))))

(def ^:no-doc yaml-request?
  (make-type-request-pred #"^(application|text)/(vnd.+)?(x-)?yaml"))

(defn make-yaml-decoder [options]
  (let [options-args (mapcat identity options)]
    (fn [s] (apply yaml/parse-string s options-args))))

(defn parse-clojure-string
  "Decode a clojure body. The body is merged into the params, so must be a map
   or a vector of key value pairs. An empty body is safely handled."
  [#^String s]
  (when-not (.isEmpty (.trim s))
    (edn/read-string {:readers *data-readers*} s)))

(def ^:no-doc clojure-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?(clojure|edn)"))

(defn ^:no-doc make-transit-decoder
  [fmt opts]
  (fn [in]
    (let [rdr (transit/reader in fmt opts)]
      (transit/read rdr))))

(def ^:no-doc transit-json-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?transit\+json"))

(def ^:no-doc transit-msgpack-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?transit\+msgpack"))

(def ^:no-doc format-adapters
  {:json {:predicate json-request?
          :decoder [make-json-decoder]}
   :json-kw {:predicate json-request?
             :decoder [make-json-decoder {:key-fn true}]}
   :edn {:predicate clojure-request?
         :decoder parse-clojure-string}
   :msgpack {:predicate msgpack-request?
             :decoder decode-msgpack
             :binary? true}
   :msgpack-kw {:predicate msgpack-request?
                :decoder (comp keywordize-keys decode-msgpack)
                :binary? true}
   :yaml {:predicate yaml-request?
          :decoder [make-yaml-decoder {:keywords false}]}
   :yaml-kw {:predicate yaml-request?
             :decoder [make-yaml-decoder]}
   :transit-json {:predicate transit-json-request?
                  :decoder [(partial make-transit-decoder :json)]
                  :binary? true}
   :transit-msgpack {:predicate transit-msgpack-request?
                     :decoder [(partial make-transit-decoder :msgpack)]
                     :binary? true}})

(def default-formats [:json :edn :msgpack :yaml :transit-msgpack :transit-json])

(defn wrap-restful-params
  "Wrapper that tries to do the right thing with the request :body and provide
   a solid basis for a RESTful API. It will deserialize to *JSON*, *YAML*, *Transit*
   or *Clojure* depending on Content-Type header. See [[wrap-format-params]] for
   more details.
   Options to specific format decoders can be passed in using *:format-options*
   option. If should be map of format keyword to options map."
  ([handler]
   (wrap-restful-params handler {}))
  ([handler {:keys [formats format-options] :as options}]
   (let [common-options (dissoc options :formats :format-options)
         adapters (for [format (or formats default-formats)
                        :when format
                        :let [adapter (if-let [data (if (map? format)
                                                      format
                                                      (get format-adapters format))]
                                        (update data :decoder (fn [decoder]
                                                                (if (vector? decoder)
                                                                  (let [[f opts] decoder]
                                                                    (f (merge opts (get format-options format))))
                                                                  decoder))))]
                        :when adapter]
                    (merge common-options adapter))]
     (reduce
       (fn [handler adapter]
         (wrap-format-params handler adapter))
       handler adapters))))
