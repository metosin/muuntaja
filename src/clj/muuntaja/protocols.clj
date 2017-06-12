(ns muuntaja.protocols
  (:require [clojure.java.io :as io]
            ring.core.protocols)
  (:import (clojure.lang IFn AFn)
           (java.io ByteArrayOutputStream ByteArrayInputStream InputStreamReader BufferedReader InputStream Writer)))

(set! *warn-on-reflection* true)

(deftype StreamableResponse [f]
  IFn
  (invoke [_ output-stream]
    (f output-stream)
    output-stream)
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(extend-protocol ring.core.protocols/StreamableResponseBody
  StreamableResponse
  (write-body-to-stream [this _ output-stream]
    ((.f this) output-stream)))

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
  (as-input-stream ^java.io.InputStream [this]))

(extend-protocol AsInputStream
  InputStream
  (as-input-stream [this] this)

  StreamableResponse
  (as-input-stream [this]
    (io/make-input-stream this nil))

  String
  (as-input-stream [this]
    (ByteArrayInputStream. (.getBytes this "utf-8")))

  nil
  (as-input-stream [_]
    (ByteArrayInputStream. (byte-array 0))))
