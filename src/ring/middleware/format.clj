(ns ring.middleware.format
  (:require [ring.middleware
             [format-params :as par]
             [format-response :as res]]))

(defn wrap-restful-format
  "Wrapper that tries to do the right thing with the request and
   response, providing a solid basis for a RESTful API. It will
   deserialize the request and serialize the response depending on
   *Content-Type* and *Accept* header. Takes a :formats argument which is
   *[:json :edn :yaml :yaml-in-html :transit-msgpack :transit-json]*
   by default. There is also a *:json-kw* and *:yaml-kw* formats which
   use keywords as keys when deserializing. The first format is also
   the default serialization format (*:json* by default). You can also
   specify error-handlers for the params parsing with *:request-error-handler*
   or the response encoding with *:response-error-handler*.
   Format options can be passed to responding middlewares using *:response-opts*
   and *:params-opts*.
   See [[ring.middleware.format-params/wrap-format-params]] and
   [[ring.middleware.format-response/wrap-format-response]] for details"
  [handler & {:keys [formats response-error-handler request-error-handler response-opts params-opts]
              :or {formats [:json :edn :yaml :yaml-in-html
                            :transit-msgpack :transit-json]
                   response-error-handler res/default-handle-error
                   request-error-handler par/default-handle-error}}]
  (-> handler
      (par/wrap-restful-params :formats formats :handle-error request-error-handler :format-options params-opts)
      (res/wrap-restful-response :formats formats :handle-error response-error-handler :format-options response-opts)))
