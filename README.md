# ring-middleware-format #

This is a set of middlewares that can be used to deserialize parameters sent in the :body of requests and serialize a Clojure data structure in the :body of a response to some string representation. It natively handles JSON, YAML, and Clojure but it can easily be extended to other custom formats. It is intended for the creation of RESTful APIs that do the right thing by default but are flexible enough to handle most special cases.

## Summary ##

To get automatic deserialization and serialization for all supported formats with sane defaults regarding headers and charsets, just do this:

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

Your routes should return raw clojure data structures where everything inside can be handled by the default encoders (no Java objects or fns mostly). If a route returns a _String_, _File_ or _InputStream_, nothing will be done. If no format can be deduced from the Accept header, JSON will be sent back by default.

## Usage ##

### Params Format Middleware ###

These middlewares are mostly lifted from https://github.com/mmcgrana/ring-json-params but generalized for arbitrary decoders. The _wrap-json-params_ is drop-in replacement for ring-json-params. They will decode the params in the request body, put them in a **:body-params** key and merge them in the **:params** key.
There are three default wrappers:

+ _wrap-json-params_
+ _wrap-yaml-params_
+ _wrap-clojure-params_

There is also a generic _wrap-format-params_ on which the others depend. Each of these wrappers take 3 optional args: **:decoder**, **:predicate** and **:charset**. See _wrap-format-params_ doc for further details.

### Response Format Middleware ###

These middlewares will take a raw data structure returned by a route and serialize it in various formats.

There are four default wrappers:

+ _wrap-json-response_
+ _wrap-yaml-response_
+ _wrap-yaml-in-html-response_ (responds to **text/html** MIME type and useful to test an API in the browser)
+ _wrap-clojure-response_

There is also a generic _wrap-format-response_ on which the others depend. Each of these wrappers take 3 optional args: **:encoders**, **:predicate**, and **:charset**. See _wrap-format-response_ doc for further details.

### Custom formats ###

You can implement custom formats in two ways:

+ If you want to slightly modify an existing wrapper you can juste pass it an argument to overload the default.
For exemple, this will cause all json formatted responses to be encoded in _iso-latin-1_:

```clojure
(-> handler
  (wrap-json-response :charset "ISO-8859-1"))
```
+ You can implement the wrapper from scratch by using both _wrap-format-params_ and _wrap-format-response_. For now, see the docs of each and how the others format were implemented for help doing this.

## See Also ##

This module aims to be both easy to use and easy to extend to new formats. However, it does not try to help with every apect of building a RESTful API, like proper error handling and method dispatching. If that is what you are looking for, you could check the modules which function more like frameworks:

+ https://github.com/malcolmsparks/plugboard
+ https://github.com/banjiewen/Clothesline
+ https://github.com/myfreeweb/ringfinger

## TODO ##

+ Some form of extensible error handling

## License ##

Copyright (C) 2011 Nils Grunwald

Distributed under the Eclipse Public License, the same as Clojure.
