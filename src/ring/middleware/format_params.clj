(ns ring.middleware.format-params
  (:require [ring.middleware.format.impl :as impl]
            [cheshire.core :as json]
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
  [{:keys [content-type] :as req}]
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
  [handler & args]
  (let [{:keys [predicate decoder charset handle-error binary?] :as options} (impl/extract-options args)
        charset (or charset get-or-guess-charset)
        handle-error (or handle-error default-handle-error)]
    (fn [{:keys [#^InputStream body] :as req}]
      (try
        (if (and body (predicate req))
          (let [byts (slurp-to-bytes body)]
            (if (> (count byts) 0)
              (let [fmt-params
                    (if binary?
                      (decoder (ByteArrayInputStream. byts))
                      (let [#^String char-enc (if (string? charset) charset (charset (assoc req :body byts)))
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

(defn make-json-decoder [{:keys [key-fn array-coerce-fn]}]
  (cond
    array-coerce-fn (fn [s] (json/parse-string s key-fn array-coerce-fn))
    key-fn          (fn [s] (json/parse-string s key-fn))
    :else           (fn [s] (json/parse-string s))))

(defn wrap-json-params
  "Handles body params in JSON format. See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder options] :as opts} (impl/extract-options args)]
    (wrap-format-params handler (assoc opts
                                       :predicate (or predicate json-request?)
                                       :decoder (or decoder (make-json-decoder options))))))

(defn wrap-json-kw-params
  "Handles body params in JSON format. Parses map keys as keywords.
   See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder options] :as opts} (impl/extract-options args)]
    (wrap-format-params handler (assoc opts
                                       :predicate (or predicate json-request?)
                                       :decoder (or decoder (make-json-decoder (assoc options :key-fn true)))))))

(def ^:no-doc msgpack-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?msgpack"))

(defn decode-msgpack [body]
  (with-open [i (clojure.java.io/input-stream (slurp-to-bytes body))]
    (let [data-input (java.io.DataInputStream. i)]
      (msgpack/unpack-stream data-input))))

(defn wrap-msgpack-params
  "Handles body params in **msgpack** format.
   See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder binary?] :as options} (impl/extract-options args)]
    (wrap-format-params handler
                        (assoc options :predicate (or predicate msgpack-request?)
                               :decoder (or decoder decode-msgpack)
                               :binary? (if (nil? binary?) true binary?)))))

(defn wrap-msgpack-kw-params
  "Handles body params in **msgpack** format.  Parses map keys as keywords.
   See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder binary?] :as options} (impl/extract-options args)]
    (wrap-format-params handler (assoc options
                                       :predicate (or predicate msgpack-request?)
                                       :decoder (or decoder #(keywordize-keys (decode-msgpack %)))
                                       :binary? (if (nil? binary?) true binary?)))))

(def ^:no-doc yaml-request?
  (make-type-request-pred #"^(application|text)/(vnd.+)?(x-)?yaml"))

(defn wrap-yaml-params
  "Handles body params in YAML format. See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder] :as options} (impl/extract-options args)]
    (wrap-format-params handler (assoc options
                                       :predicate (or predicate yaml-request?)
                                       :decoder (or decoder yaml/parse-string)))))

(defn wrap-yaml-kw-params
  "Handles body params in YAML format. Parses map keys as keywords.
   See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder] :as options} (impl/extract-options args)]
    (wrap-format-params handler (assoc options
                                       :predicate (or predicate yaml-request?)
                                       :decoder (or decoder yaml/parse-string)))))

(defn parse-clojure-string
  "Decode a clojure body. The body is merged into the params, so must be a map
   or a vector of key value pairs. An empty body is safely handled."
  [#^String s]
  (when-not (.isEmpty (.trim s))
    (edn/read-string {:readers *data-readers*} s)))

(def ^:no-doc clojure-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?(clojure|edn)"))

(defn wrap-clojure-params
  "Handles body params in Clojure (*edn*) format. See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder] :as options} (impl/extract-options args)]
    (wrap-format-params handler
                        (assoc options
                               :predicate (or predicate clojure-request?)
                               :decoder (or decoder parse-clojure-string)))))

(defn ^:no-doc make-transit-decoder
  [fmt opts]
  (fn [in]
    (let [rdr (transit/reader in fmt opts)]
      (transit/read rdr))))

(def ^:no-doc transit-json-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?transit\+json"))

(defn wrap-transit-json-params
  "Handles body params in transit format over **JSON**. You can use an *:options* key to pass
   a map with *:handlers* and *:default-handler* to transit-clj. See [[wrap-format-params]]
   for details."
  [handler & args]
  (let [{:keys [predicate decoder binary? options] :as options} (impl/extract-options args)]
    (wrap-format-params handler
                        (assoc options
                               :predicate (or predicate transit-json-request?)
                               :decoder (or decoder (make-transit-decoder :json options))
                               :binary? (if (nil? binary?) true binary?)))))

(def ^:no-doc transit-msgpack-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?transit\+msgpack"))

(defn wrap-transit-msgpack-params
  "Handles body params in transit format over **msgpack**. You can use an *:options* key to pass
   a map with *:handlers* and *:default-handler* to transit-clj. See [[wrap-format-params]] for details."
  [handler & args]
  (let [{:keys [predicate decoder binary? options] :as options} (impl/extract-options args)]
    (wrap-format-params handler
                        (assoc options
                               :predicate (or predicate transit-msgpack-request?)
                               :decoder (or decoder (make-transit-decoder :msgpack options))
                               :binary? (if (nil? binary?) true binary?)))))

(def ^:no-doc format-wrappers
  {:json wrap-json-params
   :json-kw wrap-json-kw-params
   :edn wrap-clojure-params
   :msgpack wrap-msgpack-params
   :msgpack-kw wrap-msgpack-kw-params
   :yaml wrap-yaml-params
   :yaml-kw wrap-yaml-kw-params
   :transit-json wrap-transit-json-params
   :transit-msgpack wrap-transit-msgpack-params})

(def default-formats [:json :edn :msgpack :yaml :transit-msgpack :transit-json])

(defn wrap-restful-params
  "Wrapper that tries to do the right thing with the request :body and provide
   a solid basis for a RESTful API. It will deserialize to *JSON*, *YAML*, *Transit*
   or *Clojure* depending on Content-Type header. See [[wrap-format-params]] for
   more details.
   Options to specific format decoders can be passed in using *:format-options*
   option. If should be map of format keyword to options map."
  [handler & args]
  (let [{:keys [formats format-options] :as options} (impl/extract-options args)
        common-options (dissoc options :formats :format-options)]
    (reduce (fn [h format]
              (if-let [wrapper (if
                                 (fn? format) format
                                 (format-wrappers (keyword format)))]
                (wrapper h (assoc common-options :options (get format-options format)))
                h))
            handler (or formats default-formats))))
