# Muuntaja

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

Clojure library for fast http api content negotiation - symmetric on both for servers & clients.
Standalone library, but ships with adapters for ring (async) middleware & Pedestal-style interceptors.
Explicit & extendable, supporting out-of-the-box [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn),
[MessagePack](http://msgpack.org/), [YAML](http://yaml.org/) and [Transit](https://github.com/cognitect/transit-format).

Design decisions:

- explicit configuration, no shared mutable state (e.g. multimethods)
- fast & pragmatic by default, for app-to-app communication
- extendable & pluggable: new formats, behavior
- typed exceptions - caught elsewhere
- standalone lib + adapters for ring, async-ring & pedestal
- targeting to replace [ring-middleware-defaults](https://github.com/ngrunwald/ring-middleware-format)

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

## Server Spec

### Request

* `:muuntaja.core/adapter`, holds the adapter name that was used to decode the request body, e.g. `:json`.
   Setting value to anything (e.g. `nil`) before muuntaja middleware/interceptor will skip the decoding process.
* `:muuntaja.core/accept`, holds the client-negotiated adapter name for the response, e.g. `:json`. Will be used
   later in the response pipeline.

### Response

* `:muuntaja.core/encode?`, if set to true, the response body will be encoded regardles of the type
* `:muuntaja.core/adapter`, holds the adapter name that was used to encode the response body, e.g. `:json`.
   Setting value to anything (e.g. `nil`) before muuntaja middleware/interceptor will skip the encoding process.
* `:muuntaja.core/content-type`, can be used to override the negotiated content-type for response encoding,
   e.g. setting it to `application/edn` will cause the response to encoded always with the `:edn` adapter.

## Usage

More detailed examples in the [wiki](https://github.com/metosin/muuntaja/wiki).

### Standalone

Creating a muuntaja and using it to encode & decode JSON:

```clj
(require '[muuntaja.core :as muuntaja])
(def m (muuntaja/compile muuntaja/default-options))

(muuntaja/encode m :json {:kikka 42})
; "{\"kikka\":42}"

(->> {:kikka 42}
     (muuntaja/encode m :json)
     (muuntaja/decode m :json))
; {:kikka 42}
```

With custom EDN decoder opts:

```clj
(-> (muuntaja/compile
      (-> muuntaja/default-options
          (muuntaja/with-decoder-opts :edn {:readers {'INC inc}})))
    (muuntaja/decode :edn "{:value #INC 41}"))
```

Transit-json encode fn:

```clj
(def encode-transit-json (muuntaja/encoder m :transit-json))

(slurp (encode-transit-json {:kikka 42}))
; "[\"^ \",\"~:kikka\",42]"
```

### Ring

Middleware with defaults:

```clj
(require '[muuntaja.middleware :as middleware])

(defn echo [request]
  {:status 200
   :body (:body-params request)})

(def app (middleware/wrap-format echo))

(app {:headers {"content-type" "application/json"
                "accept" "application/edn"}
      :body "{\"kikka\":42}"})
; {:status 200
;  :body "{:kikka 42}"
;  :muuntaja.core/adapter :edn
;  :headers {"Content-Type" "application/edn; charset=utf-8"}}
```

### Default options

```clj
{:extract-content-type-fn extract-content-type-ring
 :extract-accept-fn extract-accept-ring
 :decode? (constantly true)
 :encode? encode-collections-with-override
 :charset "utf-8"
 :adapters {:json {:format ["application/json" #"application/(.+\+)?json"]
                   :decoder [formats/make-json-decoder {:keywords? true}]
                   :encoder [formats/make-json-encoder]
                   :encode-protocol [formats/EncodeJson formats/encode-json]}
            :edn {:format ["application/edn" #"^application/(vnd.+)?(x-)?(clojure|edn)"]
                  :decoder [formats/make-edn-decoder]
                  :encoder [formats/make-edn-encoder]
                  :encode-protocol [formats/EncodeEdn formats/encode-edn]}
            :msgpack {:format ["application/msgpack" #"^application/(vnd.+)?(x-)?msgpack"]
                      :decoder [formats/make-msgpack-decoder {:keywords? true}]
                      :encoder [formats/make-msgpack-encoder]
                      :encode-protocol [formats/EncodeMsgpack formats/encode-msgpack]}
            :yaml {:format ["application/x-yaml" #"^(application|text)/(vnd.+)?(x-)?yaml"]
                   :decoder [formats/make-yaml-decoder {:keywords true}]
                   :encoder [formats/make-yaml-encoder]
                   :encode-protocol [formats/EncodeYaml formats/encode-yaml]}
            :transit-json {:format ["application/transit+json" #"^application/(vnd.+)?(x-)?transit\+json"]
                           :decoder [(partial formats/make-transit-decoder :json)]
                           :encoder [(partial formats/make-transit-encoder :json)]
                           :encode-protocol [formats/EncodeTransitJson formats/encode-transit-json]}
            :transit-msgpack {:format ["application/transit+msgpack" #"^application/(vnd.+)?(x-)?transit\+msgpack"]
                              :decoder [(partial formats/make-transit-decoder :msgpack)]
                              :encoder [(partial formats/make-transit-encoder :msgpack)]
                              :encode-protocol [formats/EncodeTransitMessagePack formats/encode-transit-msgpack]}}
 :formats [:json :edn :msgpack :yaml :transit-json :transit-msgpack]}
 ```

## Performance

* by default, ~6x faster than `[ring-middleware-format "0.7.0"]` (JSON request & response).
* by default, ~2x faster than `[ring/ring-json "0.4.0"]` (JSON requests & responses).

There is also a new low-level JSON encoder (in `muuntaja.json`) on top of 
[Jackson Databind](https://github.com/FasterXML/jackson-databind) and protocols supporting
hand-crafted responses => up to 5x faster than `[cheshire "5.6.3"]`.

All perf test are found in this repo.

## API Documentation

Full [API documentation](http://metosin.github.com/muuntaja) is available.

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
* Populates just the `:body-params`, not `:params` & `:json-params`/`:transit-params`
  * Because merging Persistent Maps is slow
  * if you need the `:params` add `muuntaja.middleware/wrap-params`
  * If you need `:json-params`/`:transit-params`, write your own mw for these.

### ring-middleware-format

* Set's the `:body` to nil after consuming the body (instead of re-creating a stream)
* Multiple `wrap-format` middlewares can be used in the same mw stack, rest are no-op
* By default, encodes only collections (or responses with `:muuntaja.core/encode?` set)
* Reads the `content-type` from request headers (as defined in the RING Spec)
* Does not set the `Content-Length` header (done by the adapters)
* **TODO**: does not negotiate the request charset
* **TODO**: does not negotiate the response charset
* `:yaml-in-html` / `text/html` is not supported
* `:json` `:edn` & `:yaml` responses are not wrapped into InputStreams, should they?

## License

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
