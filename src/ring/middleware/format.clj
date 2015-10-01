(ns ring.middleware.format
  (:require [ring.middleware.format-params :as par]
            [ring.middleware.format-response :as res]
            [ring.middleware.format.impl :as impl]))

(def default-formats [:json :edn :msgpack :msgpack-kw :yaml :yaml-in-html :transit-msgpack :transit-json])

(defn wrap-restful-format
  "Wrapper that tries to do the right thing with the request and
   response, providing a solid basis for a RESTful API. It will
   deserialize the request and serialize the response depending on
   *Content-Type* and *Accept* header. Takes a :formats argument which is
   *[:json :edn :msgpack :msgpack-kw :yaml :yaml-in-html :transit-msgpack :transit-json]*
   by default. There is also a *:json-kw* and *:yaml-kw* formats which
   use keywords as keys when deserializing. The first format is also
   the default serialization format (*:json* by default). You can also
   specify error-handlers for the params parsing with *:request-error-handler*
   or the response encoding with *:response-error-handler*.
   Format options can be passed to responding middlewares using *:response-options*
   and *:params-options*.
   See [[ring.middleware.format-params/wrap-format-params]] and
   [[ring.middleware.format-response/wrap-format-response]] for details"
  [handler & args]
  (let [{:keys [response-error-handler request-error-handler response-options params-options] :as options} (impl/extract-options args)
        common-options (dissoc options :response-error-handler :request-error-handler :response-options :params-options)]
    (-> handler
        (par/wrap-restful-params (assoc common-options :handle-error request-error-handler :format-options params-options))
        (res/wrap-restful-response (assoc common-options :handle-error response-error-handler :format-options response-options)))))
