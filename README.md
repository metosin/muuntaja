# Muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![cljdoc badge](https://cljdoc.xyz/badge/metosin/muuntaja)](https://cljdoc.xyz/jump/release/metosin/muuntaja)

<img src="https://raw.githubusercontent.com/metosin/muuntaja/master/doc/images/muuntaja-small.png" align="right"/>

Clojure library for fast http format negotiation, encoding and decoding. Standalone library, but ships with adapters for ring (async) middleware & Pedestal-style interceptors. Explicit & extendable, supporting out-of-the-box [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn) and [Transit](https://github.com/cognitect/transit-format) (both JSON & Msgpack). Ships with optional adapters for [MessagePack](http://msgpack.org/) and [YAML](http://yaml.org/).

Based on [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format),
but a complete rewrite ([and up to 30x faster](doc/Performance.md)).

## Rationale

- explicit configuration
- fast with good defaults
- extendable & pluggable: new formats, behavior
- typed exceptions - caught elsewhere
- support runtime docs (like swagger) & inspection (negotiation results)
- support runtime configuration (negotiation overrides)

## Modules

* `metosin/muuntaja` - the core abstractions + [Jsonista JSON](https://github.com/metosin/jsonista), EDN and Transit formats
* `metosin/muuntaja-cheshire` - optional [Cheshire JSON](https://github.com/dakrone/cheshire) format
* `metosin/muuntaja-msgpack` - Messagepack format
* `metosin/muuntaja-yaml` - YAML format

## Posts

* [Muuntaja, a boring library everyone should use](https://www.metosin.fi/blog/muuntaja/)

Check [the docs on cljdoc.org](https://cljdoc.org/d/metosin/muuntaja)
for detailed API documentation as well as more guides on how to use Muuntaja.

## Latest version

```clj
[metosin/muuntaja "0.6.4"]
```

Optionally, the parts can be required separately:

```clj
[metosin/muuntaja-cheshire "0.6.4"]
[metosin/muuntaja-msgpack "0.6.4"]
[metosin/muuntaja-yaml "0.6.4"]
```

Muuntaja requires Java 1.8+

## Quickstart

### Standalone

Use default Muuntaja instance to encode & decode JSON:

```clj
(require '[muuntaja.core :as m])

(->> {:kikka 42}
     (m/encode "application/json"))
; => #object[java.io.ByteArrayInputStream]

(->> {:kikka 42}
     (m/encode "application/json")
     slurp)
; => "{\"kikka\":42}"

(->> {:kikka 42}
     (m/encode "application/json")
     (m/decode "application/json"))
; => {:kikka 42}
```

### Ring

Automatic decoding of request body and response body encoding based on `Content-Type`, `Accept` and `Accept-Charset` headers:

```clj
(require '[muuntaja.middleware :as middleware])

(defn echo [request]
  {:status 200
   :body (:body-params request)})

; with defaults
(def app (middleware/wrap-format echo))

(def request
  {:headers
   {"content-type" "application/edn"
    "accept" "application/transit+json"}
   :body "{:kikka 42}"})
   
(app request)
; {:status 200,
;  :body #object[java.io.ByteArrayInputStream]
;  :headers {"Content-Type" "application/transit+json; charset=utf-8"}}
```

Automatic decoding of response body based on `Content-Type` header:

```clj
(-> request app m/decode-response-body)
; {:kikka 42}    
```

There is a more detailed [Ring guide](doc/With-Ring.md) too. See also [differences](doc/Differences-to-existing-formatters.md) to ring-middleware-format & ring-json.

### Interceptors

Muuntaja support [Sieppari](https://github.com/metosin/sieppari) -style interceptors too. See [`muuntaja.interceptor`](https://github.com/metosin/muuntaja/blob/master/modules/muuntaja/src/muuntaja/interceptor.clj) for details.

Interceptors can be used with [Pedestal](http://pedestal.io/) too, all but the `exception-interceptor` which conforms to the simplified exception handling model of Sieppari.

### Configuration

Explicit Muuntaja instance with custom EDN decoder options:

```clj
(def m
  (m/create
    (assoc-in
      m/default-options
      [:formats "application/edn" :decoder-opts]
      {:readers {'INC inc}})))

(->> "{:value #INC 41}"
     (m/decode m "application/edn"))
; => {:value 42}
```

Returning a function to encode transit-json:

```clj
(def encode-transit-json
  (m/encoder m "application/transit+json"))

(slurp (encode-transit-json {:kikka 42}))
; => "[\"^ \",\"~:kikka\",42]"
```

## Encoding format

By default, `encode` writes value into a `java.io.ByteArrayInputStream`. This can be changed with a `:return` option, accepting the following values:

| value            | description
|------------------|----------------------------------------------------------------------------------
| `:input-stream`  | encodes into `java.io.ByteArrayInputStream` (default)
| `:bytes`         | encodes into `byte[]`. Faster than Stream, enables NIO for servers supporting it
| `:output-stream` | encodes lazily into `java.io.OutputStream` via a callback function

All return types satisfy the following Protocols & Interfaces:

* `ring.protocols.StreamableResponseBody`, Ring 1.6.0+ will stream these for you
* `clojure.io.IOFactory`, so you can slurp the response

### `:input-stream`

```clj
(def m (m/create (assoc m/default-options :return :input-stream)))

(->> {:kikka 42}
     (m/encode m "application/json"))
; #object[java.io.ByteArrayInputStream]
```

### `:bytes`

```clj
(def m (m/create (assoc m/default-options :return :bytes)))

(->> {:kikka 42}
     (m/encode m "application/json"))
; #object["[B" 0x31f5d734 "[B@31f5d734"]
```

### `:output-stream`

```clj
(def m (m/create (assoc m/default-options :return :output-stream)))

(->> {:kikka 42}
     (m/encode m "application/json"))
; <<StreamableResponse>>
```

### Format-based return

```clj
(def m (m/create (assoc-in m/default-options [:formats "application/edn" :return] :output-stream)))

(->> {:kikka 42}
     (m/encode m "application/json"))
; #object[java.io.ByteArrayInputStream]

(->> {:kikka 42}
     (m/encode m "application/edn"))
; <<StreamableResponse>>
```

## HTTP format negotiation

HTTP format negotiation is done using request headers for both request (`content-type`, including the charset) and response (`accept` and `accept-charset`). With the default options, a full match on the content-type is required, e.g. `application/json`. Adding a `:matches` regexp for formats enables more loose matching. See [Configuration docs](doc/Configuration.md#loose-matching-on-content-type) for more info.

Results of the negotiation are published into request & response under namespaced keys for introspection. These keys can also be set manually, overriding the content negotiation process.

## Exceptions

When something bad happens, an typed exception is thrown. You should handle it elsewhere. Thrown exceptions have an `ex-data` with the following `:type` value (plus extra info to enable generating descriptive erros to clients):

* `:muuntaja/decode`, input can't be decoded with the negotiated `format` & `charset`.
* `:muuntaja/request-charset-negotiation`, request charset is illegal.
* `:muuntaja/response-charset-negotiation`, could not negotiate a charset for the response.
* `:muuntaja/response-format-negotiation`, could not negotiate a format for the response.

## Server Spec

### Request

* `:muuntaja/request`, client-negotiated request format and charset as `FormatAndCharset` record. Will
be used in the response pipeline.
* `:muuntaja/response`, client-negotiated response format and charset as `FormatAndCharset` record. Will
be used in the response pipeline.
* `:body-params` contains the decoded body.

### Response

* `:muuntaja/encode`, if set to truthy value, the response body will be encoded regardles of the type (primitives!)
* `:muuntaja/content-type`, handlers can use this to override the negotiated content-type for response encoding, e.g. setting it to `application/edn` will cause the response to be formatted in JSON.

## Options

### Default options

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

## Profiling

<img src="https://raw.githubusercontent.com/wiki/metosin/muuntaja/yklogo.png"/>

YourKit supports open source projects with its full-featured Java Profiler. YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>, innovative and intelligent tools for profiling Java and .NET applications.

## License

### [Picture](https://commons.wikimedia.org/wiki/File:Oudin_coil_Turpain.png)

By Unknown. The drawing is signed "E. Ducretet", indicating that the apparatus was made by Eugene Ducretet, a prominent Paris scientific instrument manufacturer and radio researcher. The drawing was undoubtedly originally from the Ducretet instrument catalog. [Public domain], via Wikimedia Commons.

### Original Code (ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016-2019 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License 2.0.
