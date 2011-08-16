(ns ring.middleware.format-params
  (:require [clj-json.core :as json]
            [clj-yaml.core :as yaml]))

(defn make-type-request-pred
  "Predicate that returns a predicate fn checking if Content-Type request header matches a specified regexp and body is set."
  [regexp]
  (fn [{:keys [body] :as req}]
    (if-let [#^String type (:content-type req)]
      (and body (not (empty? (re-find regexp type)))))))

(defn wrap-format-params
  [handler & {:keys [predicate decoder charset]}]
  (fn [req]
    (if (predicate req)
      (let [body (:body req)
            char-enc (if (string? charset) charset (charset req))
            bstr (slurp body :enc char-enc)
            fmt-params (decoder bstr)
            req* (assoc req
                   :body-params fmt-params
                   :params (merge (:params req) fmt-params))]
        (handler req*))
      (handler req))))

(def json-request?
  (make-type-request-pred #"^application/(vnd.+)?json"))

(defn wrap-json-params
  [handler & {:keys [predicate decoder charset]
              :or {predicate json-request?
                   decoder json/parse-string
                   charset "utf-8"}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(def yaml-request?
  (make-type-request-pred #"^(application|text)/(vnd[^+]+)?(x-)?yaml"))

(defn wrap-yaml-params
  [handler & {:keys [predicate decoder charset]
              :or {predicate yaml-request?
                   decoder yaml/parse-string
                   charset "utf-8"}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))

(def clojure-request?
  (make-type-request-pred #"^application/(vnd[^+]+)?(x-)?clojure"))

(defn wrap-clojure-params
  [handler & {:keys [predicate decoder charset]
              :or {predicate clojure-request?
                   decoder read-string
                   charset "utf-8"}}]
  (wrap-format-params handler :predicate predicate :decoder decoder :charset charset))
