# Muuntaja

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

Clojure library for handling http-api formats with web apps (both middleware & interceptors). Explicit configuration, easy to
extend. Ships with adapters for: [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn),
[MessagePack](http://msgpack.org/), [YAML](http://yaml.org/) and [Transit](https://github.com/cognitect/transit-format).

Design decisions:

- explicit configuration, avoid shared mutable state (e.g. multimethods)
- fast & pragmatic by default, intented for api usage
- extendable & pluggable: new formats, behavior
- typed exceptions but caught elsewhere
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

* Uses keywords in maps by default (good for Plumbing, Schema & Spec)

### Ring-json

* Populates just the `:body-params`, does not merge data to `:params` & protocol-spesific params like `:json-params`
  * Merging Persistent Maps is slow. You can do this with a extra middleware if you need this.

### Ring-middleware-format

* Set's the `:body` to nil after consuming the body (instead of re-creating a stream)
* By default, encodes only collections (or responses with `:muuntaja.core/encode?` set)
* Reads the `Content-Type` from headers (as the RING Spec says)
* Does not set the `Content-Length` header
* Does not negotiate the charset
* Does not negotiate the
* `:yaml-in-html` / `text/html` is not supported
* Different default return formats (**TODO**: WHY?):
  * `:json`, `:edn`, `:yaml` => `String`
  * `:msgpack`, `:transit-json`, `:transit-msgpack` => `byte[]`

## License

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
