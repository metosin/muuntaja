# Muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

<img src="https://raw.githubusercontent.com/wiki/metosin/muuntaja/muuntaja-small.png" align="right"/>

Clojure library for fast http format negotiation - symmetric for both servers & clients.
Standalone library, but ships with adapters for ring (async) middleware & Pedestal-style interceptors.
Explicit & extendable, supporting out-of-the-box [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn),
[MessagePack](http://msgpack.org/), [YAML](http://yaml.org/) and [Transit](https://github.com/cognitect/transit-format).

Based on [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format), but a complete rewrite.

## Rationale

- explicit configuration, avoiding shared mutable state (e.g. multimethods)
- fast & pragmatic by default
- extendable & pluggable: new formats, behavior
- typed exceptions - caught elsewhere
- supports runtime docs (like swagger) & inspection (negotion results)
- supports runtime configuration (negotiation overrides)

Content-negotiation is done for both request and response and covers format and charset. Default negotiation is
done using `Content-type`, `Accept` and `Accept-Charset` headers.

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

## Quickstart 

### Ring

```clj
(require '[muuntaja.middleware :as middleware])

(defn echo [request]
  {:status 200
   :body (:body-params request)})

; with defaults
(def app (middleware/wrap-format echo))

(app {:headers 
      {"content-type" "application/json"
       "accept" "application/edn"}
      :body "{\"kikka\":42}"})
; {:status 200
;  :body "{:kikka 42}"
;  :muuntaja.core/format "application/edn"
;  :headers {"Content-Type" "application/edn; charset=utf-8"}}
```

### Standalone

Create a muuntaja and use it to encode & decode JSON:

```clj
(require '[muuntaja.core :as m])

;; with defaults
(def m (m/create))

(slurp (m/encode m "application/json" {:kikka 42}))
; "{\"kikka\":42}"

(->> {:kikka 42}
     (m/encode m "application/json")
     (m/decode m "application/json"))
; {:kikka 42}
```

With custom EDN decoder opts:

```clj
(-> (m/create
      (-> m/default-options
          (m/with-decoder-opts 
            "application/edn"
            {:readers {'INC inc}})))
    (m/decode 
      "application/edn" 
      "{:value #INC 41}"))
; {:value 42}    
```

Define a function to encode Transit-json:

```clj
(def encode-transit-json (m/encoder m "application/transit+json"))

(slurp (encode-transit-json {:kikka 42}))
; "[\"^ \",\"~:kikka\",42]"
```

## Performance

* by default, over 5x faster than `[ring-middleware-format "0.7.0"]` (JSON request & response).
* by default, faster than `[ring/ring-json "0.4.0"]` (JSON requests & responses).

There is also a new low-level JSON encoder (in `muuntaja.json`) directly on top of 
[Jackson Databind](https://github.com/FasterXML/jackson-databind) and protocols supporting
hand-crafted responses => up to 5x faster than `[cheshire "5.6.3"]`.

All perf test are found in this repo.

## API Documentation

Full [API documentation](http://metosin.github.com/muuntaja) is available.

## TODO

* Currently, supports only single charset, defaulting to UTF-8.

## Server Spec

### Request

* `:muuntaja.core/format`, format name that was used to decode the request body, e.g. `application/json`. If
   the key is already present in the request map, muuntaja middleware/interceptor will skip the decoding process.
* `:muuntaja.core/accept`, client-negotiated format name for the response, e.g. `application/json`. Will
   be used later in the response pipeline.
* `:muuntaja.core/accept-charset`, client-negotiated charset for the response, e.g. `utf-8`. Will
   be used later in the response pipeline.
* `:body-params` decoded body is here.

### Response

* `:muuntaja.core/encode?`, if set to true, the response body will be encoded regardles of the type (primitives!)
* `:muuntaja.core/format`, format name that was used to encode the response body, e.g. `application/json`. If
   the key is already present in the response map, muuntaja middleware/interceptor will skip the encoding process.
* `:muuntaja.core/content-type`, handlers can use this to override the negotiated content-type for response encoding,
   e.g. setting it to `application/edn` will cause the response to be formatted in JSON.

### Default options

```clj
{:extract-content-type-fn extract-content-type-ring
 :extract-accept-charset-fn extract-accept-charset-ring
 :extract-accept-fn extract-accept-ring
 :decode? (constantly true)
 :encode? encode-collections-with-override

 :default-charset "utf-8"
 :charsets #{"utf-8"}

 :default-format "application/json"
 :formats {"application/json" {:matches #"application/(.+\+)?json"
                               :decoder [formats/make-json-decoder {:keywords? true}]
                               :encoder [formats/make-json-encoder]
                               :encode-protocol [formats/EncodeJson formats/encode-json]}
           "application/edn" {:matches #"^application/(vnd.+)?(x-)?(clojure|edn)"
                              :decoder [formats/make-edn-decoder]
                              :encoder [formats/make-edn-encoder]
                              :encode-protocol [formats/EncodeEdn formats/encode-edn]}
           "application/msgpack" {:matches #"^application/(vnd.+)?(x-)?msgpack"
                                  :decoder [formats/make-msgpack-decoder {:keywords? true}]
                                  :encoder [formats/make-msgpack-encoder]
                                  :encode-protocol [formats/EncodeMsgpack formats/encode-msgpack]}
           "application/x-yaml" {:matches #"^(application|text)/(vnd.+)?(x-)?yaml"
                                 :decoder [formats/make-yaml-decoder {:keywords true}]
                                 :encoder [formats/make-yaml-encoder]
                                 :encode-protocol [formats/EncodeYaml formats/encode-yaml]}
           "application/transit+json" {:matches #"^application/(vnd.+)?(x-)?transit\+json"
                                       :decoder [(partial formats/make-transit-decoder :json)]
                                       :encoder [(partial formats/make-transit-encoder :json)]
                                       :encode-protocol [formats/EncodeTransitJson formats/encode-transit-json]}
           "application/transit+msgpack" {:matches #"^application/(vnd.+)?(x-)?transit\+msgpack"
                                          :decoder [(partial formats/make-transit-decoder :msgpack)]
                                          :encoder [(partial formats/make-transit-encoder :msgpack)]
                                          :encode-protocol [formats/EncodeTransitMessagePack formats/encode-transit-msgpack]}}}
```

## Formats

Formats are presented as Clojure maps, registered into options under `:formats` with `content-type` as a key.
Format maps can the following optional keys:

* `:decoder` a function (or a function generator) to parse InputStreams into Clojure data structure. If the key is missing or value is `nil`, no decoding will be done.
* `:encoder` a function (or a function generator) to encode Clojure data structures into a String or an InputStream. If the key is missing or value is `nil`, no encoding will be done.
* `:decoder-opts` extra options maps for the decoder function generator.
* `:encoder-opts` extra options maps for the encoder function generator.
* `:matches` a regexp for additional matching of the content-type in request negotiation. Added for legacy support
(both ring-middleware-format & ring-json use these), e.g. `#"application/(.+\+)?json"`. Memoized behind the hoods against content-types for stellar performance.
* `:encode-protocol` vector tuple of protocol name and function that can be used to encode a data.

### Function generators

Instead of providing direct encoder/decoder functions, one can provide [Duct](https://github.com/duct-framework/duct)-style function
generator as a vector of the following elements:
1) a function of `options => encoder/decoder` (mandatory)
2) default options (optional)

To make overriding the default options easier in the call site, separate `:decode-opts` & `:encode-opts`
can be used. They are merged on top of the default options.

#### Decoder examples

```clj
;; a function
{:decoder #(cheshire.core/parse-stream (java.io.InputStreamReader. %))}

;; generator without opts
{:decoder [muuntaja.formats/make-json-decoder]}

;; generator with default opts
{:decoder [muuntaja.formats/make-json-decoder {:keywords? true}]}

;; generator with default & client opts
{:decoder [muuntaja.formats/make-json-decoder {:keywords? true}]
 :decoder-opts {:keywords? false, :bigdecimals? true}}
```

## Differences with current solutions

Both `ring-json` and `ring-middleware-format` tests have been ported to muuntaja to
verify behavior and demonstrate differences. 

### Common

* By default, uses Keywords in map keys
  * good default for `clojure.spec` & `Schema`
* No in-built exception handling
  * Exceptions have `:type` of `:muuntaja.core/decode`, catch them elsewhere
  * Add `muuntaja.middleware/wrap-exception` to catch 'em separately

### ring-json & ring-transit

* Supports multiple formats in a single middleware
* Returns Stream responses instead of Strings
* Populates just the `:body-params`, not `:params` & `:json-params`/`:transit-params`
  * Because merging Persistent Maps is slow
  * if you need the `:params` add `muuntaja.middleware/wrap-params`
  * If you need `:json-params`/`:transit-params`, write your own mw for these.

### ring-middleware-format

* Set's the `:body` to nil after consuming the body (instead of re-creating a stream)
* Multiple `wrap-format` middlewares can be used in the same mw stack, rest are no-op
* By default, encodes only collections (or responses with `:muuntaja.core/encode?` set)
* Reads the `content-type` from request headers (as defined in the RING Spec)
* Currently, supports only single charset, defaulting to UTF-8.
* Does not set the `Content-Length` header (done by the adapters)
* `:yaml-in-html` / `text/html` is not supported

## License

### [Picture](https://commons.wikimedia.org/wiki/File:Oudin_coil_Turpain.png)

By Unknown. The drawing is signed "E. Ducretet", indicating that the apparatus was made
by Eugene Ducretet, a prominent Paris scientific instrument manufacturer and radio researcher.
The drawing was undoubtedly originally from the Ducretet instrument catalog. [Public domain],
via Wikimedia Commons.

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
