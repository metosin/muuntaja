(ns muuntaja.records
  (:import (java.io Writer)))

(defrecord FormatAndCharset [^String format, ^String charset])

(defmethod print-method FormatAndCharset
  [this ^Writer w]
  (.write w (str "#FormatAndCharset" (into {} this))))

(defrecord Adapter [encode decode])
