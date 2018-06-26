(ns muuntaja.protocols
  (:require [clojure.java.io :as io]
            [muuntaja.util :as util])
  (:import (clojure.lang IFn AFn)
           (java.io ByteArrayOutputStream ByteArrayInputStream InputStreamReader BufferedReader InputStream Writer OutputStream FileInputStream File)))

(deftype StreamableResponse [f]
  IFn
  (invoke [_ output-stream]
    (f output-stream)
    output-stream)
  (applyTo [this args]
    (AFn/applyToHelper this args)))

(util/when-ns
  'ring.core.protocols
  (extend-protocol ring.core.protocols/StreamableResponseBody
    (Class/forName "[B")
    (write-body-to-stream [body _ ^OutputStream output-stream]
      (with-open [out output-stream]
        (.write out ^bytes body)))

    StreamableResponse
    (write-body-to-stream [this _ ^OutputStream output-stream]
      (with-open [out output-stream]
        ((.f this) ^OutputStream out)))))

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

(defprotocol IntoInputStream
  (into-input-stream ^java.io.InputStream [this]))

(extend-protocol IntoInputStream
  (Class/forName "[B")
  (into-input-stream [this] (ByteArrayInputStream. this))

  File
  (into-input-stream [this] (FileInputStream. this))

  InputStream
  (into-input-stream [this] this)

  StreamableResponse
  (into-input-stream [this]
    (io/make-input-stream this nil))

  String
  (into-input-stream [this]
    (ByteArrayInputStream. (.getBytes this "utf-8")))

  nil
  (into-input-stream [_]
    (ByteArrayInputStream. (byte-array 0))))
