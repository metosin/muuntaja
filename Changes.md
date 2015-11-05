# Changes for ring-middleware-format

## 0.7.0 (2015-xx-xx)

- Add support for providing options to cheshire `parse-string` and `generate-string`
- Support providing options as map instead of keyword arguments
    - Options-map: `(wrap-format {:response-options ...})`
    - Key arguments: `(wrap-format :response-options ...)`
    - In future keyword argument support could be deprecated and eventually removed
- Add support for msgpack
- Depends on `ring/ring-core` instead of catch-all packaage `ring`
- Replaced `clj-yaml` with maintained fork: [circleci/clj-yaml](https://github.com/circleci/clj-yaml)
- Updated deps:
```
[ring "1.4.0"] is available but we use "1.3.2"
[cheshire "5.5.0"] is available but we use "5.4.0"
[org.clojure/tools.reader "0.10.0"] is available but we use "0.8.16"
[com.ibm.icu/icu4j "56.1"] is available but we use "54.1"
[com.cognitect/transit-clj "0.8.285"] is available but we use "0.8.269"
```

## 0.6.0 (2015-09-01)

- Merged most of changes from Metosin fork:
    - Added missing `:predicate` option to `wrap-restful-response` middleware.
    - Support per format (Transit) options in `wrap-restful` middlewares:
        - Added `:format-options` to `wrap-restful-response` and `wrap-restful-params`
        - Added `:response-options` and `:params-options` to `wrap-restful-format`
- Escape HTML chars in YAML HTML

## 0.5.0 (2015-03-27)
### Breaking Changes
 - Allow nil to be returned as empty body with correct Content-Type instead of serialized (__Howard M. Lewis Ship__ and __curious-attempt-bunny__)

### Bugfixes
 - Fix transact format middleware (__Deraen__)
 - Actually used given error-handler in wrap-json-params (__aykuznetsova__)
 - Fix a potential bug using an unsupported feature of Clojure destructuring (__Michael Blume__)

### Other
 - Typo and test fixes (__Chris McDevitt__, __Wei Hsu__ and __ducky427__)

## 0.4.0 (2014-08-13)
### Features
 - Support for binary encodings
 - Support of Transact format over both JSON and Msgpack

### Bugfixes
 - Uses *Accept-Charset* header to choose response charset

### Other
 - Easier customizing of error handlers for `format` namespace

## 0.3.2 (2013-10-29)
### Bugfixes
  - Removed deprecated usage of cheshire.custom (__Simon Belak__)
  - Added sanity check to make sure the encoding returned by ICU4J can actually be decoded by the JVM

## 0.3.1 (2013-08-19)
### Features
  - Added `:pretty` option to JSON ( _Ian Eure_ )

### Bugfixes
  - Worked around incompatibility with _org.apache.catalina.connector.CoyoteInputStream_. Should work fine in Immutant now. ( _Roman Scherer_ )
  - Do not serialize body if entire response is nil ( _Justin Balthrop_ )

### Other
  - Fallback to looking inside `:headers` if `:content-type` is not defined at the root


## 0.3.0
### Breaking Changes
  - `wrap-format-response` encodes the body with the first format
  (`:json` by default) when unable to find an encoder matching the
  request instead of returning **306** HTTP error code

### Features
  - Added custom error handling
  - Added a `ring.middleware.format` namespace for simplified usage
  - Added a `:formats` param to customize which formats are handled
  - Use `clojure.tools.reader` for safer reading of edn
  - Added `:json-kw` and `:yaml-kw` formats and wrapper to have
    keywords keys in `:params` and `:body-params`

### Bugfixes
  - Use readers in `*data-readers*` for *edn* ( _Roman Scherer_ )

### Other
  - Better formatted doctrings ( _Anthony Grimes_ )

## 0.2.4
### Bugfixes
  - Allow empty request body as per Ring Spec ( _Roman Scherer_ )

## 0.2.3
### Bugfixes
  - Fixed bug with long request bodies when guessing character encoding

## 0.2.2
### Bugfixes
  - Fixed bug with character encoding guessing

## 0.2.1
### Features
  - Tries to guess character encoding when unspecified
  - Easier custom json types ( _Jeremy W. Sherman_ )

### Bugfixes
  - Do not try to merge vectors into :params ( _Ian Eure_ )

## 0.2.0
### Features
  - Chooses format response according to the sort order defined by Accept header ( _Jani Rahkola_ )

### Bugfixes
  - Properly lowercases header according to Ring spec ( _Luke Amdor_ )
  - Safely handles code for clojure format ( _Paul M Bauer_ )
  - safely handle empty request bodies ( _Philip Aston_ )
