(ns muuntaja.formats-perf-test
  (:require [criterium.core :as cc]
            [muuntaja.test_utils :refer :all]
            [muuntaja.formats :as formats]
            [muuntaja.json]
            [ring.core.protocols :as protocols]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

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

(defn encode-json []
  (let [encode0 (formats/make-json-string-encoder {})
        encode1 (formats/make-streaming-json-encoder {})
        encode2 (formats/make-json-encoder {})]

    ;; 4.7µs
    (title "json: string")
    (let [call #(let [baos (stream)]
                 (with-open [writer (io/writer baos)]
                   (.write writer ^String (encode0 +data+)))
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 3.1µs
    (title "json: write-to-stream")
    (let [call #(let [baos (stream)]
                 ((encode1 +data+) baos)
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 2.9µs
    (title "json: inputstream")
    (let [call #(let [baos (stream)
                      is (encode2 +data+)]
                 (io/copy is baos)
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))))

(defn encode-json-ring []
  (let [encode0 (formats/make-json-string-encoder {})
        encode1 (formats/make-streaming-json-encoder {})
        encode2 (formats/make-json-encoder {})]

    ;; 8.1µs
    (title "ring: json: string")
    (let [call #(let [baos (stream)]
                 (ring-write (encode0 +data+) baos)
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 3.0µs
    (title "ring: json: write-to-stream")
    (let [call #(let [baos (stream)]
                 (ring-write
                   (reify
                     protocols/StreamableResponseBody
                     (write-body-to-stream [_ _ stream]
                       ((encode1 +data+) stream)))
                   baos)
                 baos)]
      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 3.3µs
    (title "ring: json: inputstream")
    (let [call #(let [baos (stream)]
                 (ring-write (encode2 +data+) baos)
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 2.4µs
    (title "ring: json: inputstream (muuntaja.json)")
    (let [call #(let [baos (stream)]
                 (ring-write
                   (ByteArrayInputStream. (.getBytes (str (doto (muuntaja.json/object) (.put "kikka" 2)))))
                   baos)
                 baos)]

      (assert (= +json-string+ (str (call))))
      (cc/quick-bench
        (call)))))

(defn encode-transit-ring []
  (let [encode1 (formats/make-streaming-transit-encoder :json {})
        encode2 (formats/make-transit-encoder :json {})]

    ;; 6.6µs
    (title "ring: transit-json: write-to-stream")
    (let [call #(let [baos (stream)]
                 (ring-write
                   (reify
                     protocols/StreamableResponseBody
                     (write-body-to-stream [_ _ stream]
                       ((encode1 +data+) stream)))
                   baos)
                 baos)]

      (assert (= +transit-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 7.4µs
    (title "ring: transit-json: inputstream")
    (let [call #(let [baos (stream)]
                 (ring-write (encode2 +data+) baos)
                 baos)]

      (assert (= +transit-string+ (str (call))))
      (cc/quick-bench
        (call)))))

(defn encode-edn-ring []
  (let [encode0 (formats/make-edn-string-encoder {})
        encode2 (formats/make-edn-encoder {})]

    ;; 8.8µs
    (title "ring: edn: string")
    (let [call #(let [baos (stream)]
                 (ring-write (encode0 +data+) baos)
                 baos)]

      (assert (= +edn-string+ (str (call))))
      (cc/quick-bench
        (call)))

    ;; 4.4µs
    (title "ring: edn: inputstream")
    (let [call #(let [baos (stream)]
                 (ring-write (encode2 +data+) baos)
                 baos)]

      (assert (= +edn-string+ (str (call))))
      (cc/quick-bench
        (call)))))

(comment
  (encode-json)
  (encode-json-ring)
  (encode-transit-ring)
  (encode-edn-ring))
