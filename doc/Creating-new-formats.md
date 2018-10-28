# Creating New Formats

Formats are presented as Clojure maps, registered into options under `:formats` with format name as a key.
Format maps can the following optional keys:

| key                | description
| -------------------|---------------
| `:decoder`         | a function (or a function generator) to parse an InputStreams into Clojure data structure. If the key is missing or value is `nil`, no decoding will be done.
| `:encoder`         | a function (or a function generator) to encode Clojure data structures into an `InputStream` or to `muuntaja.protocols/Stremable`. If the key is missing or value is `nil`, no encoding will be done.
| `:opts`            | extra options maps for both the decoder and encoder function generator.
| `:decoder-opts`    | extra options maps for the decoder function generator.
| `:encoder-opts`    | extra options maps for the encoder function generator.
| `:matches`         | a regexp for additional matching of the content-type in request negotiation. Added for legacy support, e.g. `#"^application/(.+\+)?json$"`. Results of the regexp are memoized against the given content-type for near constant-time performance.

## Function generators

To allow easier customization of the formats on the client side, function generators can be used instead of plain functions. Function generators have a [Duct](https://github.com/duct-framework/duct)/[Reagent](https://github.com/reagent-project/reagent)-style vector presentations with the generator function & optionally the default opts for it.

```clj
{:decoder [json-format/make-json-decoder]}
;; => (json-format/make-json-decoder {})

{:decoder [formats/make-json-decoder {:key-fn true}]}
;; => (json-format/make-json-decoder {:key-fn true})
```

Clients can override format options with providing `:decoder-opts` or `:encoder-opts`. These get merged over the default opts.

```clj
{:decoder [formats/make-json-decoder {:key-fn true}]
 :decoder-opts {:bigdecimals? true}
;; => (json-format/make-json-decoder {:key-fn true, :bigdecimals? true})
```

## Example of a new format

```clj
(require '[muuntaja.format.json :as json-format])
(require '[clojure.string :as str])
(require '[muuntaja.core :as muuntaja])

(def streaming-upper-case-json-format
 {:decoder [json-format/make-json-decoder {:keywords? false, :key-fn str/upper-case}]
  :encoder [json-format/make-streaming-json-encoder]})

(def m
  (muuntaja/create
    (assoc-in
      muuntaja/default-options
      [:formats "application/upper-json"]
      streaming-upper-case-json-format)))

(->> {:kikka 42}
     (muuntaja/encode m "application/json")
     (muuntaja/decode m "application/upper-json"))
; {"KIKKA" 42}
```
