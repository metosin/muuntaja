(ns muuntaja.format.core)

(defprotocol Decode
  (decode [this data charset]))

(defprotocol Encode
  (encode [this data charset]))

(defprotocol EncodeToStream
  (encode-to-stream [this data charset]))

(defrecord Format [type encoder decoder matches])

(def decode-protocols [Decode])
(def encode-protocols [Encode EncodeToStream])