# Muuntaja

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

Clojure library for handling http-api formats with web apps: content negotiation, encoding and decoding.
Pluggable & configurable, ships with adapters for: [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn),
[MessagePack](http://msgpack.org/), [YAML](http://yaml.org/) and [Transit](https://github.com/cognitect/transit-format) 
(with both JSON & MessagePack -binding). Works both with Ring (middleware) and Pedestal (interceptors).

Design decisions:

- explicit configuration with good defaults, avoid shared mutable state (e.g. multimethods)
- pragmatic & fast over 100% compliancy with [HTTP spec](https://www.w3.org/Protocols/rfc2616/rfc2616.txt)
- work as standalone + has adapeters for ring, async-ring and pedestal (interceptors)
- extendable & pluggable: new formats, behavior (open-closed)
- separation of concerns: exception handling is done elsewhere, we just hint what happened
- targetting to replace [ring-middleware-defaults](https://github.com/ngrunwald/ring-middleware-format)

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

## Spec

### Request

* `:muuntaja.core/adapter`, holds the adapter name that was used to decode the request body, e.g. `:json`.
   Setting value to anything (e.g. `nil`) before muuntaja middleware/interceptor will skip the decoding process.
* `:muuntaja.core/accept`, holds the client-negotiated adapter name for the response, e.g. `:json`. Will be used
   later in the response pipeline.

### Response

* `:muuntaja.core/adapter`, holds the adapter name that was used to encode the response body, e.g. `:json`.
   Setting value to anything (e.g. `nil`) before muuntaja middleware/interceptor will skip the encoding process.
* `:muuntaja.core/content-type`, can be used to override the negotiated content-type for response encoding,
   e.g. setting it to `application/edn` will cause the response to encoded always with the `:edn` adapter.

## Usage

**TODO**

## Performance

* by default, 6x faster than `[ring-middleware-format "0.7.0"]` (JSON request & response).
* by default, 2x faster than `[ring/ring-json "0.4.0"]` (JSON requests & responses).

In addition, muuntaja ships with a low-level JSON decoder & support protocols for hand-crafting responses
directly with `Jackson`. It's up to 5x faster than `[cheshire "5.6.3"]`.

All perf test are found in this repo.

## API Documentation

Full [API documentation](http://metosin.github.com/muuntaja) is available.

## Differences with current solutions

* Populates just `:body-params`, does not merge to `:params` & `:json-params` etc.
* Set's the `:body` to nil after consuming the body
* Uses keywords in maps by default (good for Plumbing, Schema & Spec)
* `ring-json` tests have been added to muuntaja to a) verify it works b) to demonstrate differences

## License

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
