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
            bstr (slurp body char-enc)
            fmt-params (decoder bstr)
            req* (assoc req
                   :body-params fmt-params
                   :params (merge (:params req) fmt-params))]
        (handler req*))
      (handler req))))

(defn wrap-json-params
  [handler & {:keys [predicate decoder charset]
              :or {predicate json-request?
                   decoder json/parse-string
                   charset "utf-8"}}]
  (wrap-format-params :predicate predicate :decoder decoder :charset charset))


  
(defn wrap-json-params [handler]
  (fn [req]
    (if-let [body (and (json-request? req) (:body req))]
      (let [bstr (slurp body)
            json-params (json/parse-string bstr)
            req* (assoc req
                   :json-params json-params
                   :params (merge (:params req) json-params))]
        (handler req*))
      (handler req))))
