(ns muuntaja.multipart-test
  (:require [clojure.test :refer :all]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [muuntaja.format.multipart :as multipart]
            [ring.util.io :refer [string-input-stream]]))

;; Add out own tests:
;; multipart/mixed
;; multipart inside multipart
;; decode json etc. inside multipart
;; images etc. shouldn't be decoded
