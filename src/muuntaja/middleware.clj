(ns muuntaja.middleware
  (:require [muuntaja.core :as muuntaja]))

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
   (wrap-format handler muuntaja/default-options))
  ([handler options]
   (let [formats (muuntaja/compile options)]
     (fn
       ([request]
        (let [req (muuntaja/format-request formats request)]
          (->> (handler req) (muuntaja/format-response formats req))))
       ([request respond raise]
        (let [req (muuntaja/format-request formats request)]
          (handler req #(respond (muuntaja/format-response formats req %)) raise)))))))
