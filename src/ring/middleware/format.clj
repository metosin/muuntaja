(ns ring.middleware.format
  (:require [ring.middleware
             [format-params :as par]
             [format-response :as res]]))

(defn wrap-restful-format
  [handler & {:keys [formats]
              :or {formats [:json :edn :yaml :yaml-in-html]}
              :as options}]
  (-> handler
      (par/wrap-restful-params :formats formats)
      (res/wrap-restful-response :formats formats)))