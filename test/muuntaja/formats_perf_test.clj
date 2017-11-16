(ns muuntaja.formats-perf-test
  (:require [criterium.core :as cc]
            [muuntaja.test_utils :refer :all]
            [muuntaja.format.json :as json-format]
            [muuntaja.format.edn :as edn-format]
            [muuntaja.format.transit :as transit-format]
            [jsonista.core]
            [ring.core.protocols :as protocols]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [muuntaja.core :as m]
            [muuntaja.middleware :as middleware]
            [cheshire.core :as cheshire])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)
           (org.joda.time ReadableInstant)))

(set! *warn-on-reflection* true)

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(set! *warn-on-reflection* true)

(def +data+ {:kikka 2})
(def +json-string+ "{\"kikka\":2}")
(def +transit-string+ "[\"^ \",\"~:kikka\",2]")
(def +edn-string+ "{:kikka 2}")

(defn ^ByteArrayOutputStream stream []
  (ByteArrayOutputStream. 16384))

(defn ring-write [data stream]
  (protocols/write-body-to-stream
    data
    {:headers {"Content-Type" "application/json;charset=utf-8"}}
    stream))

(def +charset+ "utf-8")

(defn encode-json []
  (let [encode0 (json-format/make-json-string-encoder {})
        encode1 (json-format/make-streaming-json-encoder {})
        encode2 (json-format/make-json-encoder {})]

    ;; 4.7µs
    (title "json: string")
    (let [call #(let [baos (stream)]
                  (with-open [writer (io/writer baos)]
                    (.write writer ^String (encode0 +data+ +charset+)))
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 3.1µs
    (title "json: write-to-stream")
    (let [call #(let [baos (stream)]
                  ((encode1 +data+ +charset+) baos)
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 2.9µs
    (title "json: inputstream")
    (let [call #(let [baos (stream)
                      is (encode2 +data+ +charset+)]
                  (io/copy is baos)
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))))

(defn encode-json-ring []
  (let [encode0 (json-format/make-json-string-encoder {})
        encode1 (json-format/make-streaming-json-encoder {})
        encode2 (json-format/make-json-encoder {})]

    ;; 8.1µs
    (title "ring: json: string")
    (let [call #(let [baos (stream)]
                  (ring-write (encode0 +data+ +charset+) baos)
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 3.0µs
    (title "ring: json: write-to-stream")
    (let [call #(let [baos (stream)]
                  (ring-write
                    (reify
                      protocols/StreamableResponseBody
                      (write-body-to-stream [_ _ stream]
                        ((encode1 +data+ +charset+) stream)))
                    baos)
                  baos)]
      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 3.3µs
    (title "ring: json: inputstream")
    (let [call #(let [baos (stream)]
                  (ring-write (encode2 +data+ +charset+) baos)
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 2.4µs
    (title "ring: json: inputstream (jsonista)")
    (let [call #(let [baos (stream)]
                  (ring-write
                    (ByteArrayInputStream. (.getBytes ^String (jsonista.core/write-value-as-string {"kikka" 2})))
                    baos)
                  baos)]

      (assert (= +json-string+ (str (call))))
      (cc/bench
        (call)))))

(defn encode-transit-ring []
  (let [encode1 (transit-format/make-streaming-transit-encoder :json {})
        encode2 (transit-format/make-transit-encoder :json {})]

    ;; 6.6µs
    (title "ring: transit-json: write-to-stream")
    (let [call #(let [baos (stream)]
                  (ring-write
                    (reify
                      protocols/StreamableResponseBody
                      (write-body-to-stream [_ _ stream]
                        ((encode1 +data+ +charset+) stream)))
                    baos)
                  baos)]

      (assert (= +transit-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 7.4µs
    (title "ring: transit-json: inputstream")
    (let [call #(let [baos (stream)]
                  (ring-write (encode2 +data+ +charset+) baos)
                  baos)]

      (assert (= +transit-string+ (str (call))))
      (cc/bench
        (call)))))

(defn encode-edn-ring []
  (let [encode1 (edn-format/make-edn-string-encoder {})
        encode2 (edn-format/make-edn-encoder {})]

    ;; 8.8µs
    (title "ring: edn: string")
    (let [call #(let [baos (stream)]
                  (ring-write (encode1 +data+ +charset+) baos)
                  baos)]

      (assert (= +edn-string+ (str (call))))
      (cc/bench
        (call)))

    ;; 4.4µs
    (title "ring: edn: inputstream")
    (let [call #(let [baos (stream)]
                  (ring-write (encode2 +data+ +charset+) baos)
                  baos)]

      (assert (= +edn-string+ (str (call))))
      (cc/bench
        (call)))))

(comment
  (encode-json)
  (encode-json-ring)
  (encode-transit-ring)
  (encode-edn-ring))
