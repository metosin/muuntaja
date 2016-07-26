(ns ring.middleware.format-response
  (:require [cheshire.core :as json]
            [ring.util.response :as res]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [clojure.string :as s]
            [clojure.walk :refer [stringify-keys]]
            [cognitect.transit :as transit]
            [msgpack.core :as msgpack]
            [clojure.core.memoize :as memoize])
  (:import [java.io File InputStream
                    ByteArrayOutputStream]
           [java.nio.charset Charset]))

(set! *warn-on-reflection* true)

(def available-charsets
  "Set of recognised charsets by the current JVM"
  (into #{} (map s/lower-case (.keySet (Charset/availableCharsets)))))

(defn- serializable?
  "Predicate that returns true whenever the response body is not a
  String, File or InputStream."
  [_ {:keys [body] :as response}]
  (when response
    (not (or
           (string? body)
           (instance? File body)
           (instance? InputStream body)))))

(defn- can-encode?
  "Check whether encoder can encode to accepted-type.
  Accepted-type should have keys *:type* and *:sub-type* with appropriate
  values."
  [{:keys [enc-type]} {:keys [type sub-type]}]
  (or (= "*" type)
      (and (= (:type enc-type) type)
           (or (= "*" sub-type)
               (= (enc-type :sub-type) sub-type)))))

(defn- encoder-can-encode [type encoder]
  (if (can-encode? encoder type) encoder))

(defn- sort-by-check
  [by check headers]
  (sort-by by (fn [a b]
                (cond (= (= a check) (= b check)) 0
                      (= a check) 1
                      :else -1))
           headers))

(defn- parse-accept-header*
  "Parse Accept headers into a sorted sequence of maps.
  \"application/json;level=1;q=0.4\"
  => ({:type \"application\" :sub-type \"json\"
       :q 0.4 :parameter \"level=1\"})"
  [accept-header]
  (->> (map (fn [val]
              (let [[media-range & rest] (s/split (s/trim val) #";")
                    type (zipmap [:type :sub-type]
                                 (s/split (s/trim media-range) #"/"))]
                (cond (nil? rest)
                      (assoc type :q 1.0)
                      (= (first (s/triml (first rest)))
                         \q)                                ;no media-range params
                      (assoc type :q
                                  (Double/parseDouble
                                    (second (s/split (first rest) #"="))))
                      :else
                      (assoc (if-let [q-val (second rest)]
                               (assoc type :q
                                           (Double/parseDouble
                                             (second (s/split q-val #"="))))
                               (assoc type :q 1.0))
                        :parameter (s/trim (first rest))))))
            (s/split accept-header #","))
       (sort-by-check :parameter nil)
       (sort-by-check :type "*")
       (sort-by-check :sub-type "*")
       (sort-by :q >)))

(def ^:private parse-accept-header
  "Memoized form of [[parse-accept-header*]]"
  (memoize/fifo parse-accept-header* :fifo/threshold 500))

(defn- accept-maps [request]
  (if-let [accept (get (get request :headers) "accept" (:content-type request))]
    (if (string? accept)
      (parse-accept-header accept)
      accept)))

(defn- preferred-adapter
  "Return the encoder that encodes to the most preferred type.
  If the *Accept* header of the request is a *String*, assume it is
  according to Ring spec. Else assume the header is a sequence of
  accepted types sorted by their preference. If no accepted encoder is
  found or no *Accept* header is found, return *nil*."
  [adapters request]
  (if-let [accept (accept-maps request)]
    (some
      (fn [type]
        (if-let [adapter (some (partial encoder-can-encode type) adapters)]
          adapter))
      accept)))

(defn- parse-charset-accepted
  "Parses an *accept-charset* string to a list of [*charset* *quality-score*]"
  [v]
  (let [segments (s/split v #",")
        choices (for [segment segments
                      :when (not (empty? segment))
                      :let [[_ charset qs] (re-find #"([^;]+)(?:;\s*q\s*=\s*([0-9\.]+))?" segment)]
                      :when charset
                      :let [qscore (try
                                     (Double/parseDouble (s/trim qs))
                                     (catch Exception e 1))]]
                  [(s/trim charset) qscore])]
    choices))

(defn- preferred-charset
  "Returns an acceptable choice from a list of [*charset* *quality-score*]"
  [charsets]
  (or
    (->> (sort-by second charsets)
         (filter (comp available-charsets first))
         (first)
         (first))
    "utf-8"))

(defn default-handle-error
  "Default error handling function used, which rethrows the Exception"
  [e _ _]
  (throw e))

(defn- choose-charset*
  "Returns an useful charset from the accept-charset string.
   Defaults to utf-8"
  [accept-charset]
  (let [possible-charsets (parse-charset-accepted accept-charset)]
    (preferred-charset possible-charsets)))

(def ^:private choose-charset
  "Memoized form of [[choose-charset*]]"
  (memoize/fifo choose-charset* {} :fifo/threshold 500))

(defn resolve-response-charset
  "Default charset extractor, which returns either *Accept-Charset*
   header field or *utf-8*"
  [request]
  (if-let [accept-charset (get-in request [:headers "accept-charset"])]
    (choose-charset accept-charset)
    "utf-8"))

;; JSON

(defn make-json-encoder [options]
  (fn [s]
    (json/generate-string s options)))

;; EDN

(defn encode-edn
  [struct]
  (pr-str struct))

;; MSGPACK

(defn encode-msgpack [body]
  (with-open [out-stream (ByteArrayOutputStream.)]
    (let [data-out (java.io.DataOutputStream. out-stream)]
      (msgpack/pack-stream (stringify-keys body) data-out))
    (.toByteArray out-stream)))

(defn encode-msgpack-kw [body]
  (encode-msgpack (stringify-keys body)))

;; YAML

(defn encode-yaml [body]
  (yaml/generate-string body))

(defn- escape-html [s]
  (s/escape s {\& "&amp;"
               \< "&lt;"
               \> "&gt;"
               \" "&quot;"
               \' "&apos;"}))

(defn encode-yaml-in-html
  [body]
  (str
    "<html>\n<head></head>\n<body><div><pre>\n"
    (escape-html (yaml/generate-string body))
    "</pre></div></body></html>"))

;; Transit

(defn make-transit-encoder
  [fmt {:keys [verbose] :as options}]
  (fn [data]
    (let [out (ByteArrayOutputStream.)
          full-fmt (if (and (= fmt :json) verbose)
                     :json-verbose
                     fmt)
          wrt (transit/writer out full-fmt options)]
      (transit/write wrt data)
      (.toByteArray out))))

(defn- ->adapters [adapters {:keys [formats format-options]}]
  (->> formats
       (keep identity)
       (mapv (fn [format]
               (if-let [data (if (map? format)
                               format
                               (get adapters format))]
                 (-> data
                     (assoc :enc-type (first (parse-accept-header (:content-type data))))
                     (update :encoder (fn [encoder]
                                        (if (vector? encoder)
                                          (let [[f opts] encoder]
                                            (f (merge opts (get format-options format))))
                                          encoder)))))))
       (keep identity)))

;;
;; Public api
;;

(def format-adapters
  {:json {:content-type "application/json"
          :encoder [make-json-encoder]}
   :json-kw {:content-type "application/json"
             :encoder [make-json-encoder]}
   :edn {:content-type "application/edn"
         :encoder encode-edn}
   :msgpack {:content-type "application/msgpack"
             :encoder encode-msgpack
             :binary? true}
   :msgpack-kw {:content-type "application/msgpack"
                :encoder encode-msgpack-kw
                :binary? true}
   :clojure {:content-type "application/clojure"
             :encoder encode-edn}
   :yaml {:content-type "application/x-yaml"
          :encoder encode-yaml}
   :yaml-kw {:content-type "application/x-yaml"
             :encoder encode-yaml}
   :yaml-in-html {:content-type "text/html"
                  :encoder encode-yaml-in-html}
   :transit-json {:content-type "application/transit+json"
                  :encoder [(partial make-transit-encoder :json)]
                  :binary? true}
   :transit-msgpack {:content-type "application/transit+msgpack"
                     :encoder [(partial make-transit-encoder :msgpack)]
                     :binary? true}})

(def json-pretty {:content-type "application/json"
                  :encoder [make-json-encoder {:pretty true}]})

(def default-options {:formats [:json :yaml :edn :msgpack :clojure :yaml-in-html :transit-json :transit-msgpack]
                      :format-options {}
                      :predicate serializable?
                      :charset "utf-8"
                      :handle-error default-handle-error})

(defn format-response
  [request {:keys [body] :as response} {:keys [predicate adapters charset handle-error]}]
  (try
    (if (predicate request response)
      (let [{:keys [encoder enc-type binary?]} (or (preferred-adapter adapters request)
                                                   (first adapters))
            [body* content-type] (if binary?
                                   (let [body* (encoder body)
                                         ctype (str (enc-type :type) "/" (enc-type :sub-type))]
                                     [body* ctype])
                                   (let [^String char-enc (if (string? charset) charset (charset request))
                                         ^String body-string (if (nil? body) "" (encoder body))
                                         body* (.getBytes body-string char-enc)
                                         ctype (str (enc-type :type) "/" (enc-type :sub-type)
                                                    "; charset=" char-enc)]
                                     [body* ctype]))
            body-length (count body*)]
        (-> response
            (assoc :body (if (pos? body-length) (io/input-stream body*) nil))
            (res/content-type content-type)
            (res/header "Content-Length" body-length)))
      response)
    (catch Exception e
      (handle-error e request response))))

(defn wrap-api-response
  "Wrapper that tries to do the right thing with the response *:body*
  and provide a solid basis for a HTTP API. It will serialize to
  JSON, YAML, Clojure, Transit or HTML-wrapped YAML depending on Accept header.
  See wrap-format-response for more details. Recognized formats are
  *:json*, *:json-kw*, *:edn* *:yaml*, *:yaml-in-html*, *:transit-json*,
  *:transit-msgpack*.
  Options to specific encoders can be passed in using *:format-options*
  option. If is a map from format keyword to options map.

  Wraps a handler such that responses body to requests are formatted to
  the right format. If no *Accept* header is found, use the first encoder.

  **:formats**        sequence of either adapter names (keywords) or adapter maps.

  **:format-options** map of adapter names => options map to configure the
                      defined adapters.

  **:predicate**      is a predicate taking the request and response as
                      arguments to test if serialization should be used.

  **:charset**        can be either a string representing a valid charset or a fn
                      taking the req as argument and returning a valid charset.

  **:handle-error**   fn of [exception request response] => Any. Defaults
                      to just rethrowing the Exception."
  ([handler]
   (wrap-api-response handler {}))
  ([handler options]
   (let [options (as-> options $
                       (merge default-options $)
                       (assoc $ :adapters (->adapters format-adapters $)))]
     (fn [request]
       (format-response request (handler request) options)))))
