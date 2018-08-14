(ns muuntaja.parse-test
  (:require [clojure.test :refer :all]
            [muuntaja.parse :as parse]
            [muuntaja.test_utils :as tu]
            [criterium.core :as cc]))

(deftest parse-test
  (are [s r]
    (= r (parse/parse-content-type s))

    "application/json"
    ["application/json" nil]

    "text/html; charset=UTF-16"
    ["text/html" "utf-16"]

    "application/edn;CharSet=UTF-32"
    ["application/edn" "utf-32"])

  (are [s r]
    (= r (parse/parse-accept-charset s))

    "utf-8"
    ["utf-8"]

    "utf-8, iso-8859-1"
    ["utf-8" "iso-8859-1"]

    "utf-8, iso-8859-1;q=0.5"
    ["utf-8" "iso-8859-1"]

    "UTF-8;q=0.3,iso-8859-1;q=0.5"
    ["iso-8859-1" "utf-8"]

    ;; invalid q
    "UTF-8;q=x"
    ["utf-8"])

  (are [s r]
    (= r (parse/parse-accept s))

    nil
    nil

    ;; simple case
    "application/json"
    ["application/json"]

    ;; reordering
    "application/xml,application/xhtml+xml,text/html;q=0.9,
    text/plain;q=0.8,image/png,*/*;q=0.5"
    ["application/xml"
     "application/xhtml+xml"
     "image/png"
     "text/html"
     "text/plain"
     "*/*"]

    ;; internet explorer horror case
    "image/gif, image/jpeg, image/pjpeg, application/x-ms-application,
     application/vnd.ms-xpsdocument, application/xaml+xml,
     application/x-ms-xbap, application/x-shockwave-flash,
     application/x-silverlight-2-b2, application/x-silverlight,
     application/vnd.ms-excel, application/vnd.ms-powerpoint,
     application/msword, */*"
    ["image/gif"
     "image/jpeg"
     "image/pjpeg"
     "application/x-ms-application"
     "application/vnd.ms-xpsdocument"
     "application/xaml+xml"
     "application/x-ms-xbap"
     "application/x-shockwave-flash"
     "application/x-silverlight-2-b2"
     "application/x-silverlight"
     "application/vnd.ms-excel"
     "application/vnd.ms-powerpoint"
     "application/msword"
     "*/*"]

    ;; non q parameters
    "multipart/form-data; boundary=x; charset=US-ASCII"
    ["multipart/form-data"]

    ;; invalid q
    "text/*;q=x"
    ["text/*"]

    ;; separators in parameter values are ignored
    "text/*;x=0.0=x"
    ["text/*"]

    ;; quoted values can contain separators
    "text/*;x=\"0.0=x\""
    ["text/*"]))

(defn perf []

  (tu/title "parse-content-type")

  ;; 17ns
  (cc/quick-bench
    (parse/parse-content-type
      "application/json"))

  ;; 186ns
  (cc/quick-bench
    (parse/parse-content-type
      "application/edn;CharSet=UTF-32"))

  (tu/title "parse-accept-charset")

  ;; 1280ns
  (cc/quick-bench
    (parse/parse-accept-charset
      "utf-8"))

  ;; 2800ns
  (cc/quick-bench
    (parse/parse-accept-charset
      "UTF-8;q=0.3,iso-8859-1;q=0.5"))

  (tu/title "parse-accept")

  ;; 1100ns
  ;; 1.06us -> 3.36us
  (cc/quick-bench
    (parse/parse-accept
      "application/json"))

  ;; 8200ns
  ;; 7.14us -> 25.7us
  (cc/quick-bench
    (parse/parse-accept
      "application/xml,application/xhtml+xml,text/html;q=0.9,
       text/plain;q=0.8,image/png,*/*;q=0.5")))

(comment
  (perf))
