(ns muuntaja.middleware
  (:require [muuntaja.core :as m])
  (:import [muuntaja.core Formats]))

; [^Exception e format request]
(defn- default-on-exception [_ format _]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "Malformed " format " request.")})

(defn- try-catch [handler request on-exception]
  (try
    (handler request)
    (catch Exception e
      (if-let [data (ex-data e)]
        (if (-> data :type (= ::muuntaja/decode))
          (on-exception e (:format data) request)
          (throw e))
        (throw e)))))

(defn wrap-exception
  ([handler]
   (wrap-exception handler default-on-exception))
  ([handler on-exception]
   (fn [request]
     (try-catch handler request on-exception))))

(defn wrap-params [handler]
  (fn [request]
    (let [body-params (:body-params request)]
      (handler (if (map? body-params)
                 (update request :params merge body-params)
                 request)))))

(defn wrap-format
  ([handler]
   (wrap-format handler m/default-options))
  ([handler options-or-formats]
   (let [formats (if (instance? Formats options-or-formats)
                   options-or-formats
                   (m/create options-or-formats))]
     (fn
       ([request]
        (let [req (m/format-request formats request)]
          (->> (handler req) (m/format-response formats req))))
       ([request respond raise]
        (let [req (m/format-request formats request)]
          (handler req #(respond (m/format-response formats req %)) raise)))))))
