# Muuntaja

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

Clojure library for handling http-api formats with web apps (both middleware & interceptors). Explicit configuration, easy to
extend. Ships with adapters for: [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn),
[MessagePack](http://msgpack.org/), [YAML](http://yaml.org/) and [Transit](https://github.com/cognitect/transit-format).

Design decisions:

- explicit configuration, avoid shared mutable state (e.g. multimethods)
- fast & pragmatic by default, for api usage
- extendable & pluggable: new formats, behavior
- typed exceptions - caught elsewhere
- standalone lib + adapters for ring, async-ring & pedestal
- targeting to replace [ring-middleware-defaults](https://github.com/ngrunwald/ring-middleware-format)

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

## Spec

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

**TODO**

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

* By default, uses Keywords in map keys (good for `clojure.spec` & `Schema`)

### Ring-json

* Besides JSON, offers other protocols by default
* Populates just the `:body-params`, not `:params` & `:json-params`
  * Merging Persistent Maps is slow, if you need the `:params` there is `muuntaja.middleware/wrap-params` for this
  * If you need `:json-params`, add a extra middleware for it.
* No in-built exception handling
  * Add `muuntaja.middleware/wrap-exception` to add an exception callback

### Ring-middleware-format

* Set's the `:body` to nil after consuming the body (instead of re-creating a stream)
* Multiple Muuntaja middlewares can be used in the same middleware pipeline, first one does the deeds
* By default, encodes only collections (or responses with `:muuntaja.core/encode?` set)
* Reads the `Content-Type` from request headers (as defined in the  RING Spec)
* Does not set the `Content-Length` header (done by the adapters)
* **TODO**: does not negotiate the request charset
* **TODO**: does not negotiate the response charset
* `:yaml-in-html` / `text/html` is not supported
* Return formats are not wrapped into InputStreams, should they?
  * `:json`, `:edn`, `:yaml` => `String`
  * `:msgpack`, `:transit-json`, `:transit-msgpack` => `byte[]`

## License

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
