## 0.2.0-SNAPSHOT

* **BREAKING**: move and rename http-negotiation keys from top level to `:http` in options:
  * `:extract-content-type-fn` =>  `:extract-content-type`
  * `::extract-accept-charset-fn` => `:extract-accept-charset`
  * `:extract-accept-fn` => `:extract-accept`
  * `:decode?` => `:decode-request-body?`
  * `:encode?` => `:encode-response-body?`
* **BREAKING**: `muuntaja.options` namespace is thrown away.
  * new helpers in `muuntaja.core`: `transform-formats` & `select-formats`
  * `muuntaja.options/default-options-with-format-regexps` can be copy-pasted from below:
* default-options support all JVM registered charsets (instead of just `utf-8`)

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

* Updated deps:

```clj
[com.cognitect/transit-clj "0.8.297"] is available but we use "0.8.290"
[com.fasterxml.jackson.core/jackson-databind "2.8.5"] is available but we use "2.8.4"
```

## 0.1.0 (25.10.2016)

Initial public version.
