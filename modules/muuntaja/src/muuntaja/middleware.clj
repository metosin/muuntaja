(ns muuntaja.middleware
  (:require [muuntaja.core :as m]))

; [^Exception e format request]
(defn- default-on-exception [_ format _]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "Malformed " format " request.")})

(defn- handle-exception [exception request on-exception respond raise]
  (if-let [data (ex-data exception)]
    (if (-> data :type (= :muuntaja/decode))
      (respond (on-exception exception (:format data) request))
      (raise exception))
    (raise exception)))

(defn wrap-exception
  "Middleware that catches exceptions of type `:muuntaja/decode`
   and invokes a 3-arity callback [^Exception e format request] which
   returns a response. Support async-ring."
  ([handler]
   (wrap-exception handler default-on-exception))
  ([handler on-exception]
   (let [throw #(throw %)]
     (fn
       ([request]
        (try
          (handler request)
          (catch Exception e
            (handle-exception e request on-exception identity throw))))
       ([request respond raise]
        (try
          (handler request respond #(handle-exception % request on-exception respond raise))
          (catch Exception e
            (handle-exception e request on-exception respond throw))))))))

(defn wrap-params
  "Middleware that merges request `:body-params` into `:params`.
  Supports async-ring."
  [handler]
  (letfn [(set-params
            ([request]
             (let [params (:params request)
                   body-params (:body-params request)]
               (cond
                 (not (map? body-params)) request
                 (empty? body-params) request
                 (empty? params) (assoc request :params body-params)
                 :else (update request :params merge body-params)))))]
    (fn
      ([request]
       (handler (set-params request)))
      ([request respond raise]
       (handler (set-params request) respond raise)))))

(defn wrap-format
  "Middleware that negotiates a request body based on accept, accept-charset
  and content-type headers and decodes the body with an attached Muuntaja
  instance into `:body-params`. Encodes also the response body with the same
  Muuntaja instance based on the negotiation information or override information
  provided by the handler.

  Takes a pre-configured Muuntaja or options maps as second argument.
  See https://github.com/metosin/muuntaja for all options and defaults.
  Supports async-ring."
  ([handler]
   (wrap-format handler m/default-options))
  ([handler prototype]
   (let [m (m/create prototype)]
     (fn
       ([request]
        (let [req (m/negotiate-and-format-request m request)]
          (some->> (handler req) (m/format-response m req))))
       ([request respond raise]
        (let [req (m/negotiate-and-format-request m request)]
          (handler req #(respond (m/format-response m req %)) raise)))))))

;;
;; separate mw for negotiate, request & response
;;

(defn wrap-format-negotiate
  "Middleware that negotiates a request body based on accept, accept-charset
  and content-type headers with an attached Muuntaja instance. Injects negotiation
  results into request for `wrap-format-request` to use.

  Takes a pre-configured Muuntaja or options maps as second argument.
  See https://github.com/metosin/muuntaja for all options and defaults.
  Supports async-ring."
  ([handler]
   (wrap-format-negotiate handler m/default-options))
  ([handler prototype]
   (let [m (m/create prototype)]
     (fn
       ([request]
        (handler (m/negotiate-request-response m request)))
       ([request respond raise]
        (handler (m/negotiate-request-response m request) respond raise))))))

(defn wrap-format-request
  "Middleware that decodes the request body with an attached Muuntaja
  instance into `:body-params` based on the negotiation information provided
  by `wrap-format-negotiate`.

  Takes a pre-configured Muuntaja or options maps as second argument.
  See https://github.com/metosin/muuntaja for all options and defaults.
  Supports async-ring."
  ([handler]
   (wrap-format-request handler m/default-options))
  ([handler prototype]
   (let [m (m/create prototype)]
     (fn
       ([request]
        (handler (m/format-request m request)))
       ([request respond raise]
        (handler (m/format-request m request) respond raise))))))

(defn wrap-format-response
  "Middleware that encodes also the response body with the attached
  Muuntaja instance, based on request negotiation information provided by
  `wrap-format-negotiate` or override information provided by the handler.

  Takes a pre-configured Muuntaja or options maps as second argument.
  See https://github.com/metosin/muuntaja for all options and defaults.
  Supports async-ring."
  ([handler]
   (wrap-format-response handler m/default-options))
  ([handler prototype]
   (let [m (m/create prototype)]
     (fn
       ([request]
        (some->> (handler request) (m/format-response m request)))
       ([request respond raise]
        (handler request #(respond (m/format-response m request %)) raise))))))
