(ns muuntaja.records
  (:import (java.io Writer)))

(defrecord FormatAndCharset [^String format, ^String charset])

(defrecord Adapter [encode decode])

(defrecord Muuntaja [negotiate-content-type
                     negotiate-accept
                     negotiate-accept-charset

                     extract-content-type
                     extract-accept
                     extract-accept-charset

                     encode-response-body?
                     decode-request-body?

                     produces
                     consumes
                     matchers

                     adapters
                     charsets
                     default-charset
                     default-format])

(defmethod print-method Muuntaja
  [this ^Writer w]
  (.write w (str "#Muuntaja" (select-keys this [:produces :consumes :default-charset :default-format]))))
