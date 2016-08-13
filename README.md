# Muuntaja

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

Snappy Clojure library for managing (http) api-formats. Provides adapters for both ring, async-ring and pedestal.

Design goals:

- explicit configuration, no shared mutable state
- performance over 100% compliancy with [HTTP spec](https://www.w3.org/Protocols/rfc2616/rfc2616.txt)
- extendable & pluggable: new formats, changing behavior
- standalone + adapters for both ring (middleware) and pedestal (interceptors)
- replacement for [ring-middleware-defaults](https://github.com/ngrunwald/ring-middleware-format)

## Latest version

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

## Features

**TODO**

 - Ring compatible middleware, works with any web framework build on top of Ring
 - Automatically parses requests and encodes responses according to Content-Type and Accept headers
 - Automatically handles charset detection of requests bodies, even if the charset given by the MIME type is absent or wrong (using ICU)
 - Automatically selects and uses the right charset for the response according to the request header
 - Varied formats handled out of the box (*JSON*, *MessagePack*, *YAML*, *EDN*, *Transit over JSON or Msgpack*)
 - Pluggable system makes it easy to add to the standards encoders and decoders custom ones (proprietary format, Protobuf, specific xml, csv, etc.)

## API Documentation

Full [API documentation](http://metosin.github.com/muuntaja) is available.

## License

### [original code](https://github.com/ngrunwald/ring-middleware-format)

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi

### This library

Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
