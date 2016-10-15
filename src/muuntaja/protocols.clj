(ns muuntaja.protocols
  (:require [clojure.java.io :as io]
            [ring.core.protocols :as protocols])
  (:import (clojure.lang IFn AFn)
           (java.io ByteArrayOutputStream ByteArrayInputStream InputStreamReader BufferedReader InputStream Writer)))

(deftype StreamableResponse [f]
  protocols/StreamableResponseBody
  (write-body-to-stream [_ _ output-stream]
    (f output-stream))
  IFn
  (invoke [_ output-stream]
    (f output-stream)
    output-stream)
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(extend StreamableResponse
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [^StreamableResponse this _]
                         (with-open [out (ByteArrayOutputStream. 4096)]
                           ((.f this) out)
                           (ByteArrayInputStream.
                             (.toByteArray out))))
    :make-reader (fn [^StreamableResponse this _]
                   (with-open [out (ByteArrayOutputStream. 4096)]
                     ((.f this) out)
                     (BufferedReader.
                       (InputStreamReader.
                         (ByteArrayInputStream.
                           (.toByteArray out))))))))

(defmethod print-method StreamableResponse
  [_ ^Writer w]
  (.write w (str "<<StreamableResponse>>")))

(defprotocol AsInputStream
  (as-stream [this]))

(extend-protocol AsInputStream
  InputStream
  (as-stream [this] this)

  StreamableResponse
  (as-stream [this]
    (io/make-input-stream this nil))

  String
  (as-stream [this]
    (ByteArrayInputStream. (.getBytes this "utf-8"))))
