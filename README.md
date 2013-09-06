# ring-middleware-format #

This is a set of middlewares that can be used to deserialize parameters sent in the :body of requests and serialize a Clojure data structure in the :body of a response to some string or binary representation. It natively handles JSON, YAML, and Clojure but it can easily be extended to other custom formats. It is intended for the creation of RESTful APIs that do the right thing by default but are flexible enough to handle most special cases.

## Installation

`ring-middleware-format` is available as a Maven artifact from [Clojars](http://clojars.org/ring-middleware-format). You can add this in your `project.clj` with leiningen:

```clojure
[ring-middleware-format "0.3.1"]
```

## Summary ##

To get automatic deserialization and serialization for all supported formats with sane defaults regarding headers and charsets, just do this:

```clojure
(ns my.app
  (:use [ring.middleware.format])
  (:require [compojure.handler :as handler]))

(defroutes main-routes
  ...)

(def app
  (-> (handler/api main-routes)
      (wrap-restful-format)))
```
`wrap-restful-format` accepts an optional `:formats` parameter, which is a list of the formats that should be handled. The first format of the list is also the default serializer used when no other solution can be found. The defaults are:
```clojure
(wrap-restful-format handler :formats [:json :edn :yaml :yaml-in-html])
```

The available formats are:

  - `:json` JSON with string keys in `:params` and `:body-params`
  - `:json-kw` JSON with keywodized keys in `:params` and `:body-params`
  - `:yaml` YAML format
  - `:yaml-kw` YAML format with keywodized keys in `:params` and `:body-params`
  - `:edn` edn (native Clojure format). It uses *clojure.tools.edn* and never evals code, but uses the custom tags from `*data-readers*` 
  - `:yaml-in-html` yaml in a html page (useful for browser debugging)

Your routes should return raw clojure data structures where everything
inside can be handled by the default encoders (no Java objects or fns
mostly). If a route returns a _String_, _File_, _InputStream_ or _nil_, nothing will be done. If no format can be deduced from the **Accept** header or the format specified is unknown, the first format in the vector will be used (JSON by default).

Please note the default JSON and YAML decoder do not keywordize their output keys, if this is the behaviour you want (be careful about keywordizing user input!), you should use something like:
```clojure
(wrap-restful-format handler :formats [:json-kw :edn :yaml-kw :yaml-in-html])
```

## Usage ##

### Detailed Usage

You can separate the params and response middlewares. This allows you to use them separately, or to customize their behaviour, with specific error handling for example. See the wrappers docstrings for more details.

```clojure
(ns my.app
  (:use [ring.middleware.format-params :only [wrap-restful-params]]
        [ring.middleware.format-response :only [wrap-restful-response]])
  (:require [compojure.handler :as handler]))

(defroutes main-routes
  ...)

(def app
  (-> (handler/api main-routes)
      (wrap-restful-params)
      (wrap-restful-response)))
```

### Params Format Middleware ###

These middlewares are mostly lifted from [ring-json-params](https://github.com/mmcgrana/ring-json-params) but generalized for arbitrary decoders. The `wrap-json-params` is drop-in replacement for ring-json-params. They will decode the params in the request body, put them in a `:body-params` key and merge them in the `:params` key if they are a map.
There are four default wrappers:

+ `wrap-json-params`
+ `wrap-json-kw-params`
+ `wrap-yaml-params`
+ `wrap-clojure-params`

There is also a generic `wrap-format-params` on which the others depend. Each of these wrappers take 3 optional args: `:decoder`, `:predicate` and `:charset`. See `wrap-format-params` docstring for further details.

### Response Format Middleware ###

These middlewares will take a raw data structure returned by a route and serialize it in various formats.

There are four default wrappers:

+ `wrap-json-response`
+ `wrap-yaml-response`
+ `wrap-yaml-in-html-response` (responds to **text/html** MIME type and useful to test an API in the browser)
+ `wrap-clojure-response`

There is also a generic `wrap-format-response` on which the others depend. Each of these wrappers take 3 optional args: `:encoders`, `:predicate`, and `:charset`. See `wrap-format-response` docstring for further details.

### Custom formats ###

You can implement custom formats in two ways:

+ If you want to slightly modify an existing wrapper you can juste pass it an argument to overload the default.
For exemple, this will cause all json formatted responses to be encoded in *iso-latin-1*:

```clojure
(-> handler
  (wrap-json-response :charset "ISO-8859-1"))
```
+ You can implement the wrapper from scratch by using either or both `wrap-format-params` and `wrap-format-response`. For now, see the docs of each and how the other formats were implemented for help doing this.

## Future Work ##

+ Add [MessagePack](http://msgpack.org/) format and support binary payloads. 

## See Also ##

This module aims to be both easy to use and easy to extend to new formats. However, it does not try to help with every apect of building a RESTful API, like proper error handling and method dispatching. If that is what you are looking for, you could check the modules which function more like frameworks:

+ [Liberator](https://github.com/clojure-liberator/liberator)
+ [Plugboard](https://github.com/malcolmsparks/plugboard)
+ [Clothesline](https://github.com/banjiewen/Clothesline)
+ [Ringfinger](https://github.com/myfreeweb/ringfinger)

## License ##

Copyright (C) 2011, 2012, 2013 Nils Grunwald

Distributed under the Eclipse Public License, the same as Clojure.
