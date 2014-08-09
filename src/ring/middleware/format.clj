(ns ring.middleware.format
  (:require [ring.middleware
             [format-params :as par]
             [format-response :as res]]))

(defn wrap-restful-format
  "Wrapper that tries to do the right thing with the request and
   response, providing a solid basis for a RESTful API. It will
   deserialize the request and serialize the response depending on
   Content-Type and Accept header. Takes a :formats argument which is
   [:json :edn :yaml :yaml-in-html :transit-msgpack :transit-json]
   by default. There is also a :json-kw format which uses keywords
   as keys when deserializing. The first format is also the default
   serialization format."
  [handler & {:keys [formats]
              :or {formats [:json :edn :yaml :yaml-in-html
                            :transit-msgpack :transit-json]}
              :as options}]
  (-> handler
      (par/wrap-restful-params :formats formats)
      (res/wrap-restful-response :formats formats)))
