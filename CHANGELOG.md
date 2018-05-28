## 0.6.0-SNAPSHOT

* **BREAKING**: [Cheshire](https://github.com/dakrone/cheshire) in dropped in favor of [Jsonista](https://github.com/metosin/jsonista) as the default JSON formatter (faster, explicit configuration)
  * `muuntaja.format.json` => `muuntaja.format.cheshire`
  * `muuntaja.format.jsonista` => `muuntaja.format.json`
  * The `muuntaja.format.json` formatter takes now jsonista options directly, with an asertion to fail fast if old options are used:
     * `:key-fn` => `:encode-key-fn` and `:decode-key-fn`
     * `:bigdecimals?` => `:bigdecimals`
* move everyting from `muuntaja.records` into `muuntaja.core`
* Encoders can now return `muuntaja.protocols/ByteResponse`, a wrapper for `byte-array`. It can be streamed effectively with both Ring (via `ring.protocols/StreamableResponseBody`) and via NIO-enabled servers like [Aleph](https://github.com/ztellman/aleph) and [Immutant (perf fork)](https://github.com/ikitommi/immutant/pull/1)
  * all defaults formats (json, edn & transit) returns these by default
  * JSON is 30% snappier now with Ring Streaming.

* dropped dependencies:

```clj
[cheshire "5.8.0"]
```

* added dependencies:

```clj
[metosin/jsonista "0.2.1"]
```

## 0.5.0 (17.1.2018)

* Fix [Cannot use muuntaja.core without depending on Ring](https://github.com/metosin/muuntaja/issues/58).
* Re-implement `Muuntaja` as Protocol instead of Record. Fixes [#59](https://github.com/metosin/muuntaja/issues/59)
* Optimize `UTF-8` call path with JSONISTA, 2x faster, 20-30% perf improvement with e2e JSON echo.
* New helpers in `muuntaja.core`: `decodes`, `encodes`, `matchers`, `charsets`, `default-charset`, `default-format`, `formats` and `muuntaja?`
* Faster memoization cache for content negotiation

## 0.4.2 (9.1.2018)

* Cleanup transitive dependencies, added:

```clj
[com.fasterxml.jackson.core/jackson-core "2.9.3"]
```

## 0.4.1 (23.11.2017)

* Don't depend on Jackson internal Exceptions.

## 0.4.0 (23.11.2017)

* Require Java 1.8 (might work on older, but tests only with 1.8
* Fixed random failures if `:allow-empty-input?` was `true` - which was on by default.

## 0.3.3 (16.11.2017)

```clj
[cheshire "5.8.0"] is available but we use "5.7.1"
```

## 0.3.2 (25.7.2017)

* Custom Memoization Cache instead of `com.fasterxml.jackson.databind.util.LRUMap`, fixing [#33](https://github.com/metosin/muuntaja/issues/53).
   * No more direct dependency to `[Jackson Databind](https://github.com/FasterXML/jackson-databind)`

## 0.3.1 (19.6.2017)

* Unique names for Muuntaja interceptors.

## 0.3.0 (19.6.2017)

* **BREAKING**: Drop default support for custom encoding of records.
  * Guide how to enable it in the wiki: https://github.com/metosin/muuntaja/wiki/Configuration#custom-encoding

* **BREAKING**: Handling empty responses
  * `:allow-empty-input-on-decode?` is now called `:allow-empty-input?`. It's a boolean:
    * `true` (default): empty input(stream) is decoded into `nil`
    * `false` with cause the decoder to do whatever it does (e.g. Transit fails, Cheshire returns `nil`)

* **BREAKING**: muuntaja.json is now a separate library, [jsonista](https://github.com/metosin/jsonista)

* **BREAKING**: Muuntaja only supports Ring 1.6.0 or later. [#47](https://github.com/metosin/muuntaja/issues/47)

* jsonista decoder now allows using non-UTF charsets. [#24](https://github.com/metosin/muuntaja/issues/24)

* updated deps:

```clj
[cheshire "5.7.1"] is available but we use "5.7.0"
```

## 0.2.2 (11.6.2017)

* Support Java 1.7, fixes [#50](https://github.com/metosin/muuntaja/issues/50)

## 0.2.1 (2.4.2017)

* removed direct dependencies to msgpack, fixes [#39](https://github.com/metosin/muuntaja/issues/39).

## 0.2.0 (31.3.2017)

* optimized `muuntaja.middleware/wrap-params`, up to 3x faster for many common cases, thanks to [Dmitri Sotnikov](https://github.com/yogthos)!

* New option to allow empty input on decode, `:allow-empty-input-on-decode?` (default to `false`). If set to `true`, empty inputstreams map to `nil` body, otherwise, the decoder decides what happens (transit fails on default, cheshire does not).
  * Fixes [#33](https://github.com/metosin/muuntaja/issues/33)

* **BREAKING**: by default, `application/msgpack` and `application/x-yaml` are not used (smaller core)
  * new helpers to add formats (need to add the deps manually):
    * `application/yaml`: `[circleci/clj-yaml "0.5.5"]`
    * `application/msgpack`: `[clojure-msgpack "1.2.0" :exclusions [org.clojure/clojure]]`

```clj
(require '[muuntaja.core :as m])
(require '[muuntaja.format.msgpack :as msgpack-format])
(require '[muuntaja.format.yaml :as yaml-format])

(m/create
  (-> m/default-options
     (yaml-format/with-yaml-format)
     (msgpack-format/with-msgpack-format))

; #Muuntaja{:produces #{"application/json"
;                      "application/x-yaml"
;                      "application/msgpack"
;                      "application/transit+msgpack"
;                      "application/transit+json"
;                      "application/edn"},
;          :consumes #{"application/json"
;                      "application/x-yaml"
;                      "application/msgpack"
;                      "application/transit+msgpack"
;                      "application/transit+json"
;                      "application/edn"},
;          :default-charset "utf-8",
;          :default-format "application/json"}
```

* **Alpha**: The new `muuntaja.json` JSON encoder & decoder
  * directly on top of [Jackson](https://github.com/FasterXML/jackson)
  * explicit mappings instead of protocol extensions
  * encoding is 2.5 - 5.5x faster than Cheshire
  * decoding is 30%+ faster than Cheshire
  * not production ready, default JSON uses still Cheshire.

* All middleware support now the ring-async 3-arity version:
  * `muuntaja.middleware/wrap-exception`
  * `muuntaja.middleware/wrap-params`
  * `muuntaja.middleware/wrap-format`
  * `muuntaja.middleware/wrap-format-negotiate`
  * `muuntaja.middleware/wrap-format-request`
  * `muuntaja.middleware/wrap-format-response`

* **BREAKING**: move and rename http-negotiation keys from top level to `:http` in options:
  * `:extract-content-type-fn` =>  `:extract-content-type`
  * `:extract-accept-charset-fn` => `:extract-accept-charset`
  * `:extract-accept-fn` => `:extract-accept`
  * `:decode?` => `:decode-request-body?`
  * `:encode?` => `:encode-response-body?`
* **BREAKING**: `muuntaja.options` namespace is thrown away.
  * new helpers in `muuntaja.core`: `transform-formats` & `select-formats`
  * `muuntaja.options/default-options-with-format-regexps` can be copy-pasted from below:

```clj
(def default-options-with-format-regexps
  (-> m/default-options
      (assoc-in [:formats "application/json" :matches] #"^application/(.+\+)?json$")
      (assoc-in [:formats "application/edn" :matches] #"^application/(vnd.+)?(x-)?(clojure|edn)$")
      (assoc-in [:formats "application/msgpack" :matches] #"^application/(vnd.+)?(x-)?msgpack$")
      (assoc-in [:formats "application/x-yaml" :matches] #"^(application|text)/(vnd.+)?(x-)?yaml$")
      (assoc-in [:formats "application/transit+json" :matches] #"^application/(vnd.+)?(x-)?transit\+json$")
      (assoc-in [:formats "application/transit+msgpack" :matches] #"^application/(vnd.+)?(x-)?transit\+msgpack$"))
```

* default-options support all JVM registered charsets (instead of just `utf-8`)
* re-organized namespaces & code: formats now in separate namespaces
* fixed Accept header parsing to allow e.g. non-numeric parameters ([#67](https://github.com/ngrunwald/ring-middleware-format/pull/67))

* Updated deps:

```clj
[cheshire "5.7.0"] is available but we use "5.6.3"
[com.cognitect/transit-clj "0.8.300"] is available but we use "0.8.290"
[com.fasterxml.jackson.core/jackson-databind "2.8.7"] is available but we use "2.8.4"
```

## 0.1.0 (25.10.2016)

Initial public version.
