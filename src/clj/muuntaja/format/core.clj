(ns muuntaja.format.core)

(defprotocol Decode
  (decode [this data charset]))

(defprotocol EncodeToBytes
  (encode-to-bytes [this data charset]))

(defprotocol EncodeToOutputStream
  (encode-to-output-stream [this data charset]))

(defrecord Format [type encoder decoder matches])

(def decode-protocols [Decode])
(def encode-protocols [EncodeToBytes EncodeToOutputStream])
