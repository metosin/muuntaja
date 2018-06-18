(ns muuntaja.protocols
  (:require [clojure.java.io :as io]
            [muuntaja.util :as util])
  (:import (clojure.lang IFn AFn)
           (java.io ByteArrayOutputStream ByteArrayInputStream InputStreamReader BufferedReader InputStream Writer OutputStream FileInputStream File)
           (java.nio ByteBuffer)))

(deftype ByteResponse [bytes])

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
    (write-body-to-stream [this _ output-stream]
      (.write ^OutputStream output-stream ^bytes this))

    ByteResponse
    (write-body-to-stream [this _ output-stream]
      (.write ^OutputStream output-stream ^bytes (.bytes this)))

    StreamableResponse
    (write-body-to-stream [this _ output-stream]
      ((.f this) output-stream))))

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

(extend ByteResponse
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [^ByteResponse this _]
                         (with-open [out (ByteArrayOutputStream. 4096)]
                           (.write out ^bytes (.bytes this))
                           (ByteArrayInputStream.
                             (.toByteArray out))))
    :make-reader (fn [^ByteResponse this _]
                   (with-open [out (ByteArrayOutputStream. 4096)]
                     (.write out ^bytes (.bytes this))
                     (BufferedReader.
                       (InputStreamReader.
                         (ByteArrayInputStream.
                           (.toByteArray out))))))))

(defmethod print-method ByteResponse
  [_ ^Writer w]
  (.write w (str "<<ByteResponse>>")))

(defprotocol IntoInputStream
  (-input-stream ^java.io.InputStream [this]))

(extend-protocol IntoInputStream
  (Class/forName "[B")
  (-input-stream [this] (ByteArrayInputStream. this))

  File
  (-input-stream [this] (FileInputStream. this))

  InputStream
  (-input-stream [this] this)

  StreamableResponse
  (-input-stream [this]
    (io/make-input-stream this nil))

  ByteResponse
  (-input-stream [this]
    (io/make-input-stream this nil))

  String
  (-input-stream [this]
    (ByteArrayInputStream. (.getBytes this "utf-8")))

  nil
  (-input-stream [_]
    (ByteArrayInputStream. (byte-array 0))))
