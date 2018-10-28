# Configuration

Muuntaja is data-driven, allowing [mostly](#evil-global-state) everything to be defined via options. Muuntaja is created with `muuntaja.core/create` function. It takes either an existing Muuntaja instance, options-map or nothing as a parameter.

## Examples

```clj
(require '[muuntaja.core :as muuntaja])

;; with defaults
(def m (muuntaja/create))

;; with a muuntaja as prototype (no-op)
(muuntaja/create m)

;; with default options (same features, different instance)
(muuntaja/create muuntaja/default-options)
```

## Default options

```clj
{:http {:extract-content-type extract-content-type-ring
        :extract-accept-charset extract-accept-charset-ring
        :extract-accept extract-accept-ring
        :decode-request-body? (constantly true)
        :encode-response-body? encode-collections}

 :allow-empty-input? true
 :return :input-stream

 :default-charset "utf-8"
 :charsets available-charsets

 :default-format "application/json"
 :formats {"application/json" json-format/json-format
           "application/edn" edn-format/edn-format
           "application/transit+json" transit-format/transit-json-format
           "application/transit+msgpack" transit-format/transit-msgpack-format}}
```

## Custom options

As options are just data, normal Clojure functions can be used to modify them. Options are sanity checked at creation time. There are also the following helpers in the `muuntaja.core`:

* `select-formats`: takes a sequence of formats selecting them and setting the first one as default format.
* `transform-formats` takes an 2-arity function [format, format-options], which returns new format-options for that format. Returning `nil` removes that format. Called for all registered formats.

## Examples

### Modifying JSON encoding opts

Easiest way is to add a `:encoder-opts` key to the format with the options map as the value.

```clj
(def m
  (muuntaja/create
    (assoc-in
      muuntaja/default-options
      [:formats "application/json" :encoder-opts]
      {:key-fn #(.toUpperCase (name %))})))

(slurp (muuntaja/encode m "application/json" {:kikka 42}))
;; "{\"KIKKA\":42}"
```

### Setting Transit writers and readers

```clj
(def m
  (muuntaja/create
    (update-in
      muuntaja/default-options
      [:formats "application/transit+json"]
      merge {:decoder-opts {:handlers transit-dates/readers}
             :encoder-opts {:handlers transit-dates/writers}})))
```

### Using only selected formats

Supporting only `application/edn` and `application/transit+json` formats.

```clj
(def m
  (muuntaja/create
    (-> muuntaja/default-options
        (update
          :formats
          select-keys
          ["application/edn"
           "application/transit+json"]))))
;; clojure.lang.ExceptionInfo Invalid default format application/json
```

Ups. That didn't work. The `:default-format` is now illegal. This works:

```clj
(def m
  (muuntaja/create
    (-> muuntaja/default-options
        (update
          :formats
          select-keys
          ["application/edn"
           "application/transit+json"])
        (assoc :default-format "application/edn"))))
```

Same with `select-formats`:

```clj
(def m
  (muuntaja/create
    (muuntaja/select-formats
      muuntaja/default-options
      ["application/json"
       "application/edn"])))

(:default-format m)
; "application/edn"
```

### Disabling decoding

We have to remove `:decoder` key from all formats.

```clj
(def m
  (muuntaja/create
    (update
      muuntaja/default-options
      :formats
      #(into
        (empty %)
        (map (fn [[k v]]
               [k (dissoc v :decoder)]) %)))))

(muuntaja/encoder m "application/json")
;; #object[...]

(muuntaja/decoder m "application/json")
;; nil
```

Same with `transform-formats`:

```clj
(def m
  (muuntaja/create
    (muuntaja/transform-formats
      muuntaja/default-options
      #(dissoc %2 :encoder))))
```

### Using Streaming JSON and Transit encoders

To be used with Ring 1.6.0+, with Pedestal & other streaming libs. We have
to change the `:encoder` of the given formats:

```clj
(require '[muuntaja.format.json :as json-format])
(require '[muuntaja.format.transit :as transit-format])

(def m
  (muuntaja/create
    (-> muuntaja/default-options
        (assoc-in
          [:formats "application/json" :encoder 0]
          json-format/make-streaming-json-encoder)
        (assoc-in
          [:formats "application/transit+json" :encoder 0]
          (partial transit-format/make-streaming-transit-encoder :json))
        (assoc-in
          [:formats "application/transit+msgpack" :encoder 0]
          (partial transit-format/make-streaming-transit-encoder :msgpack)))))

(muuntaja/encode m "application/json" {:kikka 2})
;; <<StreamableResponse>>

(slurp (muuntaja/encode m "application/json" {:kikka 2}))
; "{\"kikka\":2}"
```

### Loose matching on content-type

If, for some reason, you want use the RegExps to match the content-type (like
`ring-json`, `ring-transit` & `ring-middleware-format` do. We need just to add
a `:matches` key to format with a regexp as a value. The following procudes loose
matching like in `ring-middleware-format`:

```clj
(def m
  (muuntaja/create
    (-> muuntaja/default-options
        (assoc-in [:formats "application/json" :matches] #"^application/(.+\+)?json$")
        (assoc-in [:formats "application/edn" :matches] #"^application/(vnd.+)?(x-)?(clojure|edn)$")
        ;(assoc-in [:formats "application/msgpack" :matches] #"^application/(vnd.+)?(x-)?msgpack$")
        ;(assoc-in [:formats "application/x-yaml" :matches] #"^(application|text)/(vnd.+)?(x-)?yaml$")
        (assoc-in [:formats "application/transit+json" :matches] #"^application/(vnd.+)?(x-)?transit\+json$")
        (assoc-in [:formats "application/transit+msgpack" :matches] #"^application/(vnd.+)?(x-)?transit\+msgpack$"))))

((:negotiate-content-type m) "application/vnd.foobar+json; charset=utf-8")
;; #muuntaja.core.FormatAndCharset{:format "application/json", :charset "utf-8"}
```

### Creating a new format

See [[Creating new formats]].

### Implicit configuration

Currently, both `cheshire` & `clojure-msgpack` allow new type encoders to be defined via Clojure protocol extensions. Importing a namespace could bring new type mappings or override existings without a warning, potentially breaking things. Ordering of the imports should not matter!

TODO: Make all options explicit.

* See: https://github.com/dakrone/cheshire/issues/7
