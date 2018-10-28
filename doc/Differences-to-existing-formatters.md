# Differences to existing formatters

Both `ring-json` and `ring-middleware-format` tests have been ported to muuntaja to
verify behavior and demonstrate differences.

## Middleware

### Common

* By default, Keywords are used in map keys (good for `clojure.spec` & `Schema`)
* By default, requires exact string match on content-type
  * regex-matches can be enabled manually via options
* No in-built exception handling
  * Exceptions have `:type` of `:muuntaja/*`, catch them elsewhere
  * Optionally use `muuntaja.middleware/wrap-exception` to catch 'em
* Does not merge `:body-params` into `:params`
  * Because merging persistent HashMaps is slow.
  * Optionally add `muuntaja.middleware/wrap-params` to your mw-stack before `muuntaja.middleware/wrap-format`

### Ring-json & ring-transit

* Supports multiple formats in a single middleware
* Returns Stream responses instead of Strings, `slurp` the stream to get the String
* Does not populate the `:json-params`/`:transit-params`
  * If you need these, write your own middleware for this.

### Ring-middleware-format

* Does not recreate a `:body` stream after consuming the body
* Multiple `wrap-format` (or `wrap-request`) middleware can be used in the same mw stack, only first one is effective, rest are no-op
* By default, encodes only collections (or responses with `Content-Type` header set)
  * this can be changed by setting the`[:http :encode-response-body?]` option to `(constantly true)`
* Does not set the `Content-Length` header (which is done by the ring-adapters)
* `:yaml-in-html` / `text/html` is not supported, roll you own formats if you need these
* `:yaml` and `:msgpack` are not set on by default

## Pedestal Interceptors

**TODO**

* Decoded body is set always to `:body-params`
  * Does not populate the `:json-params`/`:transit-params`, if you need these, write an extra interceptor for this.
