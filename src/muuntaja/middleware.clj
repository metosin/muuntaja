(ns muuntaja.middleware
  (:require [muuntaja.core :as m]))

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
        (if (-> data :type (= ::m/decode))
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
  ([handler prototype]
   (let [formats (m/create prototype)]
     (fn
       ([request]
        (let [req (m/format-request formats request)]
          (->> (handler req) (m/format-response formats req))))
       ([request respond raise]
        (let [req (m/format-request formats request)]
          (handler req #(respond (m/format-response formats req %)) raise)))))))

;;
;; separate mw for negotiate, request & response
;;

(defn wrap-format-request
  ([handler]
   (wrap-format-request handler m/default-options))
  ([handler prototype]
   (let [formats (m/create prototype)]
     (fn
       ([request]
        (handler (m/decode-ring-request formats request)))
       ([request respond raise]
        (handler (m/format-request formats request) respond raise))))))

(defn wrap-format-negotiate
  ([handler]
   (wrap-format-negotiate handler m/default-options))
  ([handler prototype]
   (let [formats (m/create prototype)]
     (fn
       ([request]
        (handler (m/negotiate-ring-request formats request)))
       ([request respond raise]
        (handler (m/negotiate-ring-request formats request) respond raise))))))

(defn wrap-format-response
  ([handler]
   (wrap-format-response handler m/default-options))
  ([handler prototype]
   (let [formats (m/create prototype)]
     (fn
       ([request]
        (->> (handler request) (m/format-response formats request)))
       ([request respond raise]
        (handler request #(respond (m/format-response formats request %)) raise))))))
