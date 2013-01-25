(ns ring.middleware.format-params
  (:require [cheshire.custom :as json]
            [clj-yaml.core :as yaml])
  (:import [com.ibm.icu.text CharsetDetector]
           [java.io ByteArrayInputStream InputStream ByteArrayOutputStream]))

(defn guess-charset
  [{:keys [#^bytes body]}]
  (try
    (let [#^CharsetDetector detector (CharsetDetector.)]
      (.enableInputFilter detector true)
      (.setText detector body)
      (let [m (.detect detector)
            encoding (.getName m)]
        encoding))
    (catch Exception _ nil)))

(defn get-charset
  "Extracts charset from Content-Type header."
  [{:keys [content-type] :as req}]
  (if content-type
    (second (re-find #";\s*charset=([^\s;]+)" content-type))))

(defn get-or-guess-charset
  "Tries to guess the encoding if not given in content-type"
  [req]
  (or
   (get-charset req)
   (guess-charset req)
   "utf-8"))

(defn get-or-default-charset
  "Returns utf-8 encoding if not given in content-type"
  [req]
  (or
   (get-charset req)
   "utf-8"))

(defn make-type-request-pred
  "Predicate that returns a predicate fn checking if Content-Type request header matches a specified regexp and body is set."
  [regexp]
  (fn [{:keys [body] :as req}]
    (if-let [#^String type (:content-type req)]
      (and body (not (empty? (re-find regexp type)))))))

(defn slurp-to-bytes
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

(defn wrap-format-params
  "Wraps a handler such that requests body are deserialized from to the right format, added in a :body-params key and merged in :params. It takes 3 args:
:predicate is a predicate taking the request as sole argument to test if deserialization should be used.
:decoder specifies a fn taking the body String as sole argument and giving back a hash-map.
:charset can be either a string representing a valid charset or a fn taking the req as argument and returning a valid charset."
  [handler & {:keys [predicate decoder charset]}]
  (fn [{:keys [#^InputStream body] :as req}]
    (if-let [byts (slurp-to-bytes body)]
      (if (predicate req)
        (let [body (:body req)
              #^String char-enc (if (string? charset) charset (charset (assoc req :body byts)))
              bstr (String. byts char-enc)
              fmt-params (decoder bstr)
              req* (assoc req
                     :body-params fmt-params
                     :params (merge (:params req)
                                    (when (map? fmt-params) fmt-params)))]
          (handler req*))
        (handler (assoc req :body (ByteArrayInputStream. byts))))
      (handler req))))

(def json-request?
  (make-type-request-pred #"^application/(vnd.+)?json"))

(defn wrap-json-params
  "Handles body params in JSON format. See wrap-format-params for details."
  [handler & {:keys [predicate decoder charset]
              :or {predicate json-request?
                   decoder json/parse-string
                   charset get-or-guess-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(def yaml-request?
  (make-type-request-pred #"^(application|text)/(vnd.+)?(x-)?yaml"))

(defn wrap-yaml-params
  "Handles body params in YAML format. See wrap-format-params for details."
  [handler & {:keys [predicate decoder charset]
              :or {predicate yaml-request?
                   decoder yaml/parse-string
                   charset get-or-guess-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(defn safe-read-string [str]
  "Parses clojure input using the reader in a safe manner by disabling eval in the reader."
  (binding [*read-eval* false]
    (read-string str)))

(defn parse-clojure-string [#^String s]
  "Decode a clojure body. The body is merged into the params, so must be a map or a vector of
key value pairs. An empty body is safely handled."
  (when (not (.isEmpty (.trim s)))
    (safe-read-string s)))

(def clojure-request?
  (make-type-request-pred #"^application/(vnd.+)?(x-)?clojure"))

(defn wrap-clojure-params
  "Handles body params in Clojure format. See wrap-format-params for details."
  [handler & {:keys [predicate decoder charset]
              :or {predicate clojure-request?
                   decoder parse-clojure-string
                   charset get-or-guess-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(defn wrap-restful-params
  "Wrapper that tries to do the right thing with the request :body and provide a solid basis for a RESTful API.
It will deserialize to JSON, YAML or Clojure depending on Content-Type header. See wrap-format-response for more details."
  [handler]
  (-> handler
      (wrap-json-params)
      (wrap-clojure-params)
      (wrap-yaml-params)))
