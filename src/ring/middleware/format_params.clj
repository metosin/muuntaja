(ns ring.middleware.format-params
  (:require [cheshire.core :as json]
            [clj-yaml.core :as yaml]))

(defn get-charset
  "Extracts charset from Content-Type header. utf-8 by default."
  [{:keys [content-type] :as req}]
  (let [default-charset "utf-8"]
    (if content-type
      (or (second (re-find #";\s*charset=([^\s;]+)" content-type)) default-charset)
      default-charset)))

(defn make-type-request-pred
  "Predicate that returns a predicate fn checking if Content-Type request header matches a specified regexp and body is set."
  [regexp]
  (fn [{:keys [body] :as req}]
    (if-let [#^String type (:content-type req)]
      (and body (not (empty? (re-find regexp type)))))))

(defn wrap-format-params
    "Wraps a handler such that requests body are deserialized from to the right format, added in a :body-params key and merged in :params. It takes 3 args:
:predicate is a predicate taking the request as sole argument to test if deserialization should be used.
:decoder specifies a fn taking the body String as sole argument and giving back a hash-map.
:charset can be either a string representing a valid charset or a fn taking the req as argument and returning a valid charset."
  [handler & {:keys [predicate decoder charset]}]
  (fn [req]
    (if (predicate req)
      (let [body (:body req)
            char-enc (if (string? charset) charset (charset req))
            bstr (slurp body :encoding char-enc)
            fmt-params (decoder bstr)
            req* (assoc req
                   :body-params fmt-params
                   :params (merge (:params req) fmt-params))]
        (handler req*))
      (handler req))))

(def json-request?
  (make-type-request-pred #"^application/(vnd.+)?json"))

(defn wrap-json-params
  "Handles body params in JSON format. See wrap-format-params for details."
  [handler & {:keys [predicate decoder charset]
              :or {predicate json-request?
                   decoder json/parse-string
                   charset get-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(def yaml-request?
  (make-type-request-pred #"^(application|text)/(vnd.+)?(x-)?yaml"))

(defn wrap-yaml-params
  "Handles body params in YAML format. See wrap-format-params for details."
  [handler & {:keys [predicate decoder charset]
              :or {predicate yaml-request?
                   decoder yaml/parse-string
                   charset get-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(defn safe-read-string [str]
  "Parses clojure input using the reader in a safe manner by disabling eval in the reader."
  (binding [*read-eval* false]
    (read-string str)))

(defn parse-clojure-string [s]
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
                   charset get-charset}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(defn wrap-restful-params
  "Wrapper that tries to do the right thing with the request :body and provide a solid basis for a RESTful API.
It will deserialize to JSON, YAML or Clojure depending on Content-Type header. See wrap-format-response for more details."
  [handler]
  (-> handler
      (wrap-json-params)
      (wrap-clojure-params)
      (wrap-yaml-params)))
