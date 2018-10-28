# Usage with Ring

## Simplest thing that works

Ring application that can read and write JSON, EDN and Transit:

```clj
(require '[muuntaja.middleware :as mw])

(defn handler [_]
  {:status 200
   :body {:ping "pong"}})

;; with defaults
(def app (mw/wrap-format handler))

(def request {:headers {"accept" "application/json"}})

(->> request app)
; {:status 200,
;  :body #object[java.io.ByteArrayInputStream 0x1d07d794 "java.io.ByteArrayInputStream@1d07d794"],
;  :headers {"Content-Type" "application/json; charset=utf-8"}}

(->> request app :body slurp)
; "{\"ping\":\"pong\"}"
```

## With Muuntaja instance

Like previous, but with custom Transit options and a standalone Muuntaja:

```clj
(require '[cognitect.transit :as transit])
(require '[muuntaja.middleware :as mw])
(require '[muuntaja.core :as m])

;; custom Record
(defrecord Ping [])

;; custom transit handlers
(def write-handlers
  {Ping (transit/write-handler (constantly "Ping") (constantly {}))})

(def read-handlers
  {"Ping" (transit/read-handler map->Ping)})

;; a configured Muuntaja
(def muuntaja
  (m/create
    (update-in
      m/default-options
      [:formats "application/transit+json"]
      merge
      {:encoder-opts {:handlers write-handlers}
       :decoder-opts {:handlers read-handlers}})))

(defn endpoint [_]
  {:status 200
   :body {:ping (->Ping)}})

(def app (-> endpoint (mw/wrap-format muuntaja)))

(def request {:headers {"accept" "application/transit+json"}})

(->> request app)
; {:status 200,
;  :body #object[java.io.ByteArrayInputStream 0x3478e74b "java.io.ByteArrayInputStream@3478e74b"],
;  :headers {"Content-Type" "application/transit+json; charset=utf-8"}}

(->> request app :body slurp)
; "[\"^ \",\"~:ping\",[\"~#Ping\",[\"^ \"]]]"

(->> request app :body (m/decode muuntaja "application/transit+json"))
; {:ping #user.Ping{}}
```

## Middleware Chain

Muuntaja doesn't catch formatting exceptions itself, but throws them instead. If you want to format those also, you need to split the `wrap-format` into parts.

This:

```clj
(-> app (mw/wrap-format muuntaja))
```

Can be written as:

```clj
(-> app
    ;; format the request
    (mw/wrap-format-request muuntaja)
    ;; format the response
    (mw/wrap-format-response muuntaja)
    ;; negotiate the request & response formats
    (mw/wrap-format-negotiate muuntaja))
```

Now you can add your own exception-handling middleware between the `wrap-format-request` and `wrap-format-response`. It can catch the formatting exceptions and it's results are written with the response formatter.

Here's a "complete" stack:

```clj
(-> app
    ;; support for `:params`
    (mw/wrap-params)
    ;; format the request
    (mw/wrap-format-request muuntaja)
    ;; catch exceptions
    (mw/wrap-exceptions my-exception-handler)
    ;; format the response
    (mw/wrap-format-response muuntaja)
    ;; negotiate the request & response formats
    (mw/wrap-format-negotiate muuntaja))
```

See example of real-life use from [compojure-api](https://github.com/metosin/compojure-api/blob/master/src/compojure/api/middleware.clj). It also reads the `:produces` and `:consumes` from Muuntaja instance and passed them to [Swagger](swagger.io) docs. `:params`-support is needed to allow compojure destucturing syntax.
