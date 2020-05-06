## 0.6.7 

* new module `muuntaja-form` to handle `application/x-www-form-urlencoded` using [ring-codec](https://github.com/ring-clojure/ring-codec) by [Mathieu Lirzin](https://github.com/metosin/muuntaja/pull/110)

* Update deps:

```clj
[metosin/jsonista "0.2.6"] is available but we use "0.2.5"
[com.cognitect/transit-clj "1.0.324"] is available but we use "0.8.319"
```

* Update optional deps:

```clj
[ring/ring-codec "1.1.2"]
[cheshire "5.10.0"] is available but we use "5.9.0"
[clj-commons/clj-yaml "0.7.1"] is available but we use "0.7.0"
```

## 0.6.6 (2019-11-07)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.5...master)**

* Fix handler chaining when `nil` is returned from handler.

## 0.6.5 (2019-10-07)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.4...master)**

* Update deps:

```clj
[metosin/jsonista "0.2.5"] is available but we use "0.2.2"
[com.cognitect/transit-clj "0.8.319"] is available but we use "0.8.313"
```

## 0.6.4 (2019-04-01)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.3...0.6.4)**

* Update clj-yaml to new clj-commons fork, for Java 11 support

## 0.6.3 (2018-12-15)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.2...0.6.3)**

* new helper `m/decode-response-body` to return response body, decoded based on response `Content-Type` header. Throws if the body can't be decoded.

```clj
(-> {:headers {"accept" "application/edn"}}
    ((middleware/wrap-format
       (constantly {:status 200, :body {:date (Date. 0)}})))
    (m/decode-response-body))
; {:date #inst"1970-01-01T00:00:00.000-00:00"}
```

## 0.6.2 (2018-12-09)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.1...0.6.2)**

* Added 2-arity to `encode` & `decode`, using the default instance, fixes [#86](https://github.com/metosin/muuntaja/issues/86), thanks to [valerauko](https://github.com/valerauko).

```clj
(require '[muuntaja.core :as m])

(->> {:lonely "planet"}
     (m/encode "application/edn")
     (m/decode "application/edn"))
; => {:lonely "planet"}
```

## 0.6.1 (2018-09-22)

**[compare](https://github.com/metosin/muuntaja/compare/0.6.0...0.6.1)**

### `muuntaja`

* Updated deps:

```clj
[metosin/jsonista "0.2.2"] is available but we use "0.2.1"
```

### `muuntaja-cheshire`

* Updated deps:

```clj
[cheshire "5.8.1"] is available but we use "5.8.0"
```

## 0.6.0 (2018-09-03)

**[compare](https://github.com/metosin/muuntaja/compare/0.5.0...0.6.0)**

* all the changes in the 0.6.0 alphas

### Cumulative changes

* **BREAKING**: rewrote the interceptors
  * based on the [Sieppari](https://github.com/metosin/sieppari) interceptor model
  * just like the middleware in `muuntaja.middleware`, but `muuntaja.interceptors`.
    * `exception-interceptor` ~= `wrap-exception`
    * `params-interceptor` ~= `wrap-params`
    * `format-interceptor` ~= `wrap-format`
    * `format-negotiate-interceptor` ~= `wrap-format-negotiate`
    * `format-request-interceptor` ~= `wrap-format-request`
    * `format-response-interceptor` ~= `wrap-format-response`

* Use `:default-charset` for response encoding if found anywhere in the `accept` header, fixes [#79](https://github.com/metosin/muuntaja/issues/79) 
* Publish the raw content-negotiation results into `FormatAndCharset` too
* Added helpers `m/get-request-format-and-charset` and `get-response-format-and-charset`

```clj
(require '[muuntaja.middleware :as middleware])
(require '[muuntaja.core :as m])

(->> {:headers {"content-type" "application/edn; charset=utf-16"
                "accept" "cheese/cake"
                "accept-charset" "cheese-16"}}
     ((middleware/wrap-format identity))
     ((juxt m/get-request-format-and-charset
            m/get-response-format-and-charset)))
;[#FormatAndCharset{:format "application/edn"
;                   :charset "utf-16"
;                   :raw-format "application/edn"}
; #FormatAndCharset{:format "application/json"
;                   :charset "utf-8"
;                   :raw-format "cheese/cake"}]
```

* **BREAKING**: If `:body-params` is set in request, don't try to decode request body.

* **BREAKING**: Changes in special Muuntaja keys in request & response
  * `:muuntaja/format` is not published into request or response
  * `muuntaja.core/disable-request-encoding` helper is removed
  * `muuntaja.core/disable-response-encoding` helper is removed
  * `:muuntaja/encode?` response key (to force encoding) is renamed to `:muuntaja/encode`

* **BREAKING**: [Cheshire](https://github.com/dakrone/cheshire) in dropped in favor of [Jsonista](https://github.com/metosin/jsonista) as the default JSON formatter (faster, explicit configuration)
  * `muuntaja.format.json` => `muuntaja.format.cheshire`
  * `muuntaja.format.jsonista` => `muuntaja.format.json`
  * The `muuntaja.format.json` formatter takes now jsonista options directly, with an asertion to fail fast if old options are used:
     * `:key-fn` => `:encode-key-fn` and `:decode-key-fn`
     * `:bigdecimals?` => `:bigdecimals`
  * `:mapper` option can be used to set the preconfigured `ObjectMapper`.
* move everyting from `muuntaja.records` into `muuntaja.core`
* `m/slurp` to consume whatever Muuntaja can encode into a String. Not performance optimized, e.g. for testing.
* **BREAKING**: formats are written as Protocols instead of just functions.
  * encoders should satisfy `muuntaja.format.core/EncodeToBytes` or `muuntaja.format.core/EncodeToOutputStream`
  * decoders should satisfy `muuntaja.format.core/Decode`
  * as a migration guard - if functions are used, there is an descrptive error message at Muuntaja creation time
* With Muuntaja option `:return` one can control what is the encoding target. Valid values are:

| value            | description                                                                      |
| -----------------|----------------------------------------------------------------------------------|
| `:input-stream`  | encodes into `java.io.ByteArrayInputStream` (default)                            |
| `:bytes`         | encodes into `byte[]`. Faster than Stream, enables NIO for servers supporting it |
| `:output-stream` | encodes lazily into `java.io.OutputStream` via a callback function               |

* Formats can override `:return` value
* Formats can also have `:name` (e.g. `"application/json"`) and for installing new formats there is `muuntaja.core/install`
* **BREAKING**: Muuntaja has now a multi-module layout! The modules are:
  * `metosin/muuntaja` - the core + [Jsonista](https://github.com/metosin/jsonista), EDN and Transit formats
  * `metosin/muuntaja-cheshire` - optional [Cheshire](https://github.com/dakrone/cheshire) JSON format
  * `metosin/muuntaja-msgpack` - Messagepack format
  * `metosin/muuntaja-yaml` - YAML format

```clj
(require '[muuntaja.core :as m])

;; [metosin/muuntaja-msgpack]
(require '[muuntaja.format.msgpack])

;; [metosin/muuntaja-yaml]
(require '[muuntaja.format.yaml])

(def m (m/create
         (-> m/default-options
             (m/install muuntaja.format.msgpack/format)
             (m/install muuntaja.format.yaml/format)
             (assoc :return :bytes))))
```

* **BREAKING**: `with-streaming-***-format` helpers are removed, use `:return` `:output-stream` to set it.
* formats can now also take `:opts` keys, which gets merged into encoder and decoder arguments and opts, so these decoders are effectively the same:

```clj
(require '[muuntaja.core :as m])

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :opts]
      {:decode-key-fn false}))
  "application/json")

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :decoder-opts]
      {:decode-key-fn false}))
  "application/json")
```

... and also this:

```clj  
(require '[jsonista.core :as j])

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :opts]
      {:mapper (j/object-mapper {:decode-key-fn false})}))
  "application/json")  
```

* dropped dependencies:

```clj
[cheshire "5.8.0"]
```

* updated deps:

```clj
[metosin/jsonista "0.2.1"]
[com.cognitect/transit-clj "0.8.313"] is available but we use "0.8.309"
```

## 0.6.0-alpha5

* **BREAKING**: rewrote the interceptors
  * based on the [Sieppari](https://github.com/metosin/sieppari) interceptor model
  * just like the middleware in `muuntaja.middleware`, but `muuntaja.interceptors`.
    * `exception-interceptor` ~= `wrap-exception`
    * `params-interceptor` ~= `wrap-params`
    * `format-interceptor` ~= `wrap-format`
    * `format-negotiate-interceptor` ~= `wrap-format-negotiate`
    * `format-request-interceptor` ~= `wrap-format-request`
    * `format-response-interceptor` ~= `wrap-format-response`

## 0.6.0-alpha4

* Use `:default-charset` for response encoding if found anywhere in the `accept` header, fixes [#79](https://github.com/metosin/muuntaja/issues/79) 
* Publish the raw content-negotiation results into `FormatAndCharset` too
* Removed helpers `m/get-negotiated-request-content-type` and `m/get-negotiated-response-content-type`
* Added helpers `m/get-request-format-and-charset` and `get-response-format-and-charset`

```clj
(require '[muuntaja.middleware :as middleware])
(require '[muuntaja.core :as m])

(->> {:headers {"content-type" "application/edn; charset=utf-16"
                "accept" "cheese/cake"
                "accept-charset" "cheese-16"}}
     ((middleware/wrap-format identity))
     ((juxt m/get-request-format-and-charset
            m/get-response-format-and-charset)))
;[#FormatAndCharset{:format "application/edn"
;                   :charset "utf-16"
;                   :raw-format "application/edn"}
; #FormatAndCharset{:format "application/json"
;                   :charset "utf-8"
;                   :raw-format "cheese/cake"}]
```

* updated deps:

```clj
[com.cognitect/transit-clj "0.8.313"] is available but we use "0.8.309"
```

## 0.6.0-alpha3

* If `:body-params` is set in request, don't try to decode request body.

## 0.6.0-alpha2

* **BREAKING**: Changes in special Muuntaja keys in request & response
  * `:muuntaja/format` is not published into request or response
  * `muuntaja.core/disable-request-encoding` helper is removed
  * `muuntaja.core/disable-response-encoding` helper is removed
  * `:muuntaja/encode?` response key (to force encoding) is renamed to `:muuntaja/encode`
* **BREAKING**: if a "Content-Type" header is set in a response, the body will not be encoded, unless `:muuntaja/encode` is set

## 0.6.0-alpha1

* **BREAKING**: [Cheshire](https://github.com/dakrone/cheshire) in dropped in favor of [Jsonista](https://github.com/metosin/jsonista) as the default JSON formatter (faster, explicit configuration)
  * `muuntaja.format.json` => `muuntaja.format.cheshire`
  * `muuntaja.format.jsonista` => `muuntaja.format.json`
  * The `muuntaja.format.json` formatter takes now jsonista options directly, with an asertion to fail fast if old options are used:
     * `:key-fn` => `:encode-key-fn` and `:decode-key-fn`
     * `:bigdecimals?` => `:bigdecimals`
  * `:mapper` option can be used to set the preconfigured `ObjectMapper`.
* move everyting from `muuntaja.records` into `muuntaja.core`
* helpers `m/get-negotiated-request-content-type` & `m/get-negotiated-response-content-type`
* `m/slurp` to consume whatever Muuntaja can encode into a String. Not performance optimized, e.g. for testing.
* **BREAKING**: formats are written as Protocols instead of just functions.
  * encoders should satisfy `muuntaja.format.core/EncodeToBytes` or `muuntaja.format.core/EncodeToOutputStream`
  * decoders should satisfy `muuntaja.format.core/Decode`
  * as a migration guard - if functions are used, there is an descrptive error message at Muuntaja creation time
* With Muuntaja option `:return` one can control what is the encoding target. Valid values are:

| value            | description                                                                      |
| -----------------|----------------------------------------------------------------------------------|
| `:input-stream`  | encodes into `java.io.ByteArrayInputStream` (default)                            |
| `:bytes`         | encodes into `byte[]`. Faster than Stream, enables NIO for servers supporting it |
| `:output-stream` | encodes lazily into `java.io.OutputStream` via a callback function               |

* Formats can override `:return` value
* Formats can also have `:name` (e.g. `"application/json"`) and for installing new formats there is `muuntaja.core/install`
* **BREAKING**: Muuntaja has now a multi-module layout! The modules are:
  * `metosin/muuntaja` - the core + [Jsonista](https://github.com/metosin/jsonista), EDN and Transit formats
  * `metosin/muuntaja-cheshire` - optional [Cheshire](https://github.com/dakrone/cheshire) JSON format
  * `metosin/muuntaja-msgpack` - Messagepack format
  * `metosin/muuntaja-yaml` - YAML format

```clj
(require '[muuntaja.core :as m])

;; [metosin/muuntaja-msgpack]
(require '[muuntaja.format.msgpack])

;; [metosin/muuntaja-yaml]
(require '[muuntaja.format.yaml])

(def m (m/create
         (-> m/default-options
             (m/install muuntaja.format.msgpack/format)
             (m/install muuntaja.format.yaml/format)
             (assoc :return :bytes))))
```

* **BREAKING**: `with-streaming-***-format` helpers are removed, use `:return` `:output-stream` to set it.
* formats can now also take `:opts` keys, which gets merged into encoder and decoder arguments and opts, so these decoders are effectively the same:

```clj
(require '[muuntaja.core :as m])

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :opts]
      {:decode-key-fn false}))
  "application/json")

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :decoder-opts]
      {:decode-key-fn false}))
  "application/json")
```

... and also this:

```clj  
(require '[jsonista.core :as j])

(m/decoder
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/json" :opts]
      {:mapper (j/object-mapper {:decode-key-fn false})}))
  "application/json")  
```

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
