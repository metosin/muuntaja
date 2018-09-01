# Performance

## Background

Muuntaja has been built with performance in mind, while still doing mostly everything in Clojure:

* single middleware/interceptor for all formats (instead of stacked middleware)
* avoid run-time regexps
* avoid dynamic bindings
* avoid Clojure (map) destructuring
* (Java-backed) memoized content negotiation
* Protocols over Multimethods
* Records over Maps
* use field access instead of lookups
* keep all core functions small enough to enable JVM Inlining
* unroll generic functions (like `get-in`)
* use streaming when possible

The codebase contains the performance test, done using [Criterium](https://github.com/hugoduncan/criterium) under the `perf` Leiningen profile with a 2013 Mackbook pro.

**NOTE:** Tests are not scientific proof and may contain errors. If you have idea how to test things better, please poke us.

## Middleware

Muuntaja is tested against the current common ring-based formatters. It's fastest in [all tests](https://github.com/metosin/muuntaja/blob/master/test/muuntaja/core_perf_test.clj).

### `[ring/ring-json "0.4.0"]` & `[ring-transit "0.1.6"]`

* ok performance by default, but only provide a single format - Stacking these separate middleware makes the pipeline slower.

### `[ring-middleware-format "0.7.0"]`

* has really bad defaults:
   * with 1K JSON, Muuntaja is 10-30x faster (depending on the JSON encoder used)
   * with 100k JSON, Muuntaja is still 2-4x faster
* with tuned r-m-f options
   * with <1K messages, Muuntaja is still much faster
   * similar perf on large messages

### JSON
![perf-json-relative](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/perf-json-relative.png)
![perf-json-relative2](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/perf-json-relative2.png)
![perf-json](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/perf-json.png)

## Transit
![perf-transit](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/perf-transit.png)
![perf-transit-relative](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/perf-transit-relative.png)

## Interceptors

Pedestal:
 * `io.pedestal.http.content-negotiation/negotiate-content` for content negotiation
 * `io.pedestal.http.body-params/body-params` for decoding the request body
 * `io.pedestal.http/json-body`, `io.pedestal.http/transit-json-body` etc. to encode responses

Muuntaja:
 * `muuntaja.interceptor/format-negotiate` for content negotiation
 * `muuntaja.interceptor/format-request` for decoding request body
 * `muuntaja.interceptor/format-response` to encode responses
 * `muuntaja.interceptor/format` all on one step

![interceptor-perf-json-relative2](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/interceptors-perf-json-relative.png)
![interceptor-perf-json-relative](https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/interceptors-perf-json.png)
