# Muuntaja

**TODO**

# muuntaja [![Continuous Integration status](https://secure.travis-ci.org/metosin/muuntaja.png)](http://travis-ci.org/metosin/muuntaja) [![Dependencies Status](http://jarkeeper.com/metosin/muuntaja/status.svg)](http://jarkeeper.com/metosin/muuntaja)

This is a set of middlewares that can be used to deserialize parameters sent in the :body of requests and serialize a Clojure data structure in the :body of a response to some string or binary representation. It natively handles JSON, MessagePack, YAML, Transit over JSON or Msgpack and Clojure (edn) but it can easily be extended to other custom formats, both string and binary. It is intended for the creation of RESTful APIs that do the right thing by default but are flexible enough to handle most special cases.

## Installation ##

Latest stable version:

[![Clojars Project](http://clojars.org/metosin/muuntaja/latest-version.svg)](http://clojars.org/metosin/muuntaja)

Add this to your dependencies in `project.clj`.

## Features ##

 - Ring compatible middleware, works with any web framework build on top of Ring
 - Automatically parses requests and encodes responses according to Content-Type and Accept headers
 - Automatically handles charset detection of requests bodies, even if the charset given by the MIME type is absent or wrong (using ICU)
 - Automatically selects and uses the right charset for the response according to the request header
 - Varied formats handled out of the box (*JSON*, *MessagePack*, *YAML*, *EDN*, *Transit over JSON or Msgpack*)
 - Pluggable system makes it easy to add to the standards encoders and decoders custom ones (proprietary format, Protobuf, specific xml, csv, etc.)

## API Documentation ##

Full [API documentation](http://metosin.github.com/muuntaja) is available.


## License ##

Copyright &copy; 2011, 2012, 2013, 2014 Nils Grunwald<br>
Copyright &copy; 2015, 2016 Juho Teperi<br>
Copyright &copy; 2016 Metosin

Distributed under the Eclipse Public License, the same as Clojure.
