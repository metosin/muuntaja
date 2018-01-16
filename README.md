# Muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja)

<img src="https://raw.githubusercontent.com/wiki/metosin/muuntaja/muuntaja-small.png" align="right"/>

Clojure library for fast http format negotiation, encoding and decoding. Standalone library, but
ships with adapters for ring (async) middleware & Pedestal-style interceptors. Explicit & extendable, supporting
out-of-the-box [JSON](http://www.json.org/), [EDN](https://github.com/edn-format/edn) and [Transit](https://github.com/cognitect/transit-format) (both JSON & Msgpack).
Ships with optional adapters for [MessagePack](http://msgpack.org/) and [YAML](http://yaml.org/).

Based on [ring-middleware-format](https://github.com/ngrunwald/ring-middleware-format),
but a complete rewrite ([and up to 30x faster](https://github.com/metosin/muuntaja/wiki/Performance)).

## Rationale

- both standalone & http (ring & pedestal)
- explicit configuration over mutable state (e.g. multimethods)
- lazy streams when possible
- fast with good defaults
- extendable & pluggable: new formats, behavior
- typed exceptions - caught elsewhere
- support runtime docs (like swagger) & inspection (negotiation results)
- support runtime configuration (negotiation overrides)

Check the [Wiki](https://github.com/metosin/muuntaja/wiki) & [api-docs](http://metosin.github.com/muuntaja) for more details.

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

Muuntaja requires Java 1.8+

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
      {"content-type" "application/edn"
       "accept" "application/json"}
      :body "{:kikka 42}"})
; {:status 200,
;  :body #object[java.io.ByteArrayInputStream]
;  :muuntaja/format "application/json",
;  :headers {"Content-Type" "application/json; charset=utf-8"}}
```

There is a [Ring guide](https://github.com/metosin/muuntaja/wiki/With-Ring) in a wiki. See also [differences](https://github.com/metosin/muuntaja/wiki/Differences-to-existing-formatters) to ring-middleware-format & ring-json if you are migrating from those.

## Interceptors

See [`muuntaja.interceptor`](https://github.com/metosin/muuntaja/blob/master/src/clj/muuntaja/interceptor.clj).

## Standalone

Create a Muuntaja and use it to encode & decode JSON:

```clj
(require '[muuntaja.core :as m])

;; with defaults
(def m (muuntaja/create))

(->> {:kikka 42}
     (m/encode m "application/json")
     slurp)
; => "{\"kikka\":42}"

(->> {:kikka 42}
     (m/encode m "application/json")
     (m/decode m "application/json"))
; => {:kikka 42}
```

With custom EDN decoder opts:

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

A function to encode Transit-json:

```clj
(def encode-transit-json
  (m/encoder m "application/transit+json"))

(slurp (encode-transit-json {:kikka 42}))
; => "[\"^ \",\"~:kikka\",42]"
```

## Streaming

Muuntaja ships with streaming encoders for both JSON & Transit. With these, the encoded data
can be lazily written to provided `OutputStream`, avoiding intermediate byte-streams. These encoders
return a `muuntaja.protocols.StreamableResponse` type, which satisifies the following protocols & interfaces:

* `ring.protocols.StreamableResponseBody`, Ring 1.6.0 will stream these for you
* `clojure.lang.IFn`, invoke the result with an OutputStream to write the results into the stream
* `clojure.io.IOFactory`, so you can slurp the response

```clj
(require '[muuntaja.format.json :as json-format])

(def m
  (m/create
    (-> m/default-options
        json-format/with-streaming-json-format)))

(->> {:kikka 42}
     (m/encode m "application/json"))
; <<StreamableResponse>>

(->> {:kikka 42}
     (m/encode m "application/json")
     slurp)
; "{\"kikka\":42}"

(->> {:kikka 42}
     (m/encode m "application/json")
     (m/decode m "application/json"))
; {:kikka 42}
```

## HTTP format negotiation

HTTP format negotiation is done via request headers for both request (`content-type`, including the charset)
and response (`accept` and `accept-charset`). With the default options, a full match on the content-type is
required, e.g. `application/json`. Adding a `:matches` regexp for formats enables more loose matching. See [Configuration wiki-page](https://github.com/metosin/muuntaja/wiki/Configuration#loose-matching-on-content-type) for more info.

Results of the negotiation are published into request & response under namespaced keys for introspection.
These keys can also be set manually, overriding the content negotiation process.

## Exceptions

When something bad happens, an typed exception is thrown. You should handle it elsewhere. Thrown exceptions
have an `ex-data` with the following `:type` value (plus extra info to generate descriptive erros to clients):

* `:muuntaja/decode`, input can't be decoded with the negotiated `format` & `charset`.
* `:muuntaja/request-charset-negotiation`, request charset is illegal.
* `:muuntaja/response-charset-negotiation`, could not negotiate a charset for the response.
* `:muuntaja/response-format-negotiation`, could not negotiate a format for the response.

## Server Spec

### Request

* `:muuntaja/format`, format name that was used to decode the request body, e.g. `application/json`. If
   the key is already present in the request map, muuntaja middleware/interceptor will skip the decoding process.
* `:muuntaja/request`, client-negotiated request format and charset as `FormatAndCharset` record. Will
be used in the response pipeline.
* `:muuntaja/response`, client-negotiated response format and charset as `FormatAndCharset` record. Will
be used in the response pipeline.
* `:body-params` decoded body is here.

### Response

* `:muuntaja/encode?`, if set to true, the response body will be encoded regardles of the type (primitives!)
* `:muuntaja/format`, format name that was used to encode the response body, e.g. `application/json`. If
   the key is already present in the response map, muuntaja middleware/interceptor will skip the encoding process.
* `:muuntaja/content-type`, handlers can use this to override the negotiated content-type for response encoding,
   e.g. setting it to `application/edn` will cause the response to be formatted in JSON.


### Options

#### Default options

```clj
{:http {:extract-content-type extract-content-type-ring
        :extract-accept-charset extract-accept-charset-ring
        :extract-accept extract-accept-ring
        :decode-request-body? (constantly true)
        :encode-response-body? encode-collections-with-override}

 :allow-empty-input? true

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

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

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

Copyright &copy; 2016-2017 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
