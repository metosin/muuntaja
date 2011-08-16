(ns ring.middleware.format-params
  (:require [clj-json.core :as json]))

(defn make-type-request-pred
  "Predicate that returns a predicate fn checking if Content-Type request header matches a specified regexp and body is set."
  [regexp]
  (fn [{:keys [body] :as req}]
    (if-let [#^String type (:content-type req)]
      (and body (not (empty? (re-find regexp type)))))))

(def json-request?
  (make-type-request-pred #"^application/(vnd.+)?json"))

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
