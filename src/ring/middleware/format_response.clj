(ns ring.middleware.format-response
  (:require [cheshire.custom :as json]
            [ring.util.response :as res]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [clojure.string :as s])
  (:use [clojure.core.memoize :only [memo-lu]])
  (:import [java.io File InputStream BufferedInputStream]))

(defn serializable?
  "Predicate that returns true whenever the response body is not a
  String, File or InputStream."
  [_ {:keys [body]}]
  (not (or
        (string? body)
        (instance? File body)
        (instance? InputStream body))))

(defn can-encode?
  "Check whether encoder can encode to accepted-type.
  Accepted-type should have keys :type and :sub-type with appropriate
  values."
  [{:keys [enc-type] :as encoder} {:keys [type sub-type] :as accepted-type}]
  (or (= "*" type)
      (and (= (:type enc-type) type)
           (or (= "*" sub-type)
               (= (enc-type :sub-type) sub-type)))))

(defn sort-by-check
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
                         \q) ;no media-range params
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

(def parse-accept-header (memo-lu parse-accept-header* 500))

(defn preferred-encoder
  "Return the encoder that encodes to the most preferred type.
  If the Accept header of the request is a string, assume it is
  according to Ring spec. Else assume the header is a sequence of
  accepted types sorted by their preference. If no accepted encoder is
  found, return nil. If no Accept header is found, return the first
  encoder."
  [encoders req]
  (if-let [accept (get-in req [:headers "accept"])]
    (first (for [accepted-type (if (string? accept)
                                 (parse-accept-header accept)
                                 accept)
                 encoder encoders
                 :when (can-encode? encoder accepted-type)]
             encoder))
    (first encoders)))

(defn make-encoder
  "Return a encoder map suitable for wrap-format-response.
   f takes a string and returns an encoded string
   type Content-Type of the encoded string
   (make-encoder json/generate-string \"application/json\")"
  [encoder content-type]
  {:encoder encoder
   :enc-type (first (parse-accept-header content-type))})

(defn default-handle-error
  [e _ _]
  (throw e))

(defn wrap-format-response
  "Wraps a handler such that responses body to requests are formatted to
  the right format. If no Accept header is found, use the first encoder.
  :predicate is a predicate taking the request and response as
             arguments to test if serialization should be used
  :encoders a sequence of maps given by make-encoder
  :charset can be either a string representing a valid charset or a fn
           taking the req as argument and returning a valid charset
           (utf-8 is strongly suggested)
  :handle-error is a fn with a sig [exception request response]. Defaults
                to just rethrowing the Exception"
  [handler & {:keys [predicate encoders charset handle-error]}]
  (fn [req]
    (let [{:keys [headers body] :as response} (handler req)]
      (try
        (if (predicate req response)
          (let [{:keys [encoder enc-type]} (preferred-encoder encoders req)]
            (if (nil? encoder)
              (throw (RuntimeException. "cannot find encoder for response"))
              (let [^String char-enc (if (string? charset) charset (charset req))
                    ^String body-string (encoder body)
                    body* (.getBytes body-string char-enc)
                    body-length (count body*)]
                (-> response
                    (assoc :body (io/input-stream body*))
                    (res/content-type (str (enc-type :type) "/" (enc-type :sub-type)
                                           "; charset=" char-enc))
                    (res/header "Content-Length" body-length)))))
          response)
        (catch Exception e
          (handle-error e req response))))))

(defn wrap-json-response
  "Wrapper to serialize structures in :body to JSON with sane defaults.
  See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset handle-error]
              :or {predicate serializable?
                   encoder json/generate-string
                   type "application/json"
                   charset "utf-8"
                   handle-error default-handle-error}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoders [(make-encoder encoder type)]
                        :charset charset
                        :handle-error handle-error))

;; Functions for Clojure native serialization

(defn- generate-native-clojure
  [struct]
  (pr-str struct))

(defn- generate-hf-clojure
  [struct]
  (binding [*print-dup* true]
    (pr-str struct)))

(defn wrap-clojure-response
  "Wrapper to serialize structures in :body to Clojure native with sane defaults.
  If :hf is set to true, will use *print-dup* for high-fidelity
  printing ( see
  https://groups.google.com/d/msg/clojure/5wRBTPNu8qo/1dJbtHX0G-IJ ).
  See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset hf handle-error]
              :or {predicate serializable?
                   encoder generate-native-clojure
                   type "application/edn"
                   charset "utf-8"
                   hf false
                   handle-error default-handle-error}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoders [(make-encoder
                                    (if hf generate-hf-clojure encoder)
                                    type)]
                        :charset charset
                        :handle-error handle-error))

(defn wrap-yaml-response
  "Wrapper to serialize structures in :body to YAML with sane
  defaults. See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset handle-error]
              :or {predicate serializable?
                   encoder yaml/generate-string
                   type "application/x-yaml"
                   charset "utf-8"
                   handle-error default-handle-error}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoders [(make-encoder encoder type)]
                        :charset charset
                        :handle-error handle-error))

(defn- wrap-yaml-in-html
  [body]
  (str
   "<html>\n<head></head>\n<body><div><pre>\n"
   (yaml/generate-string body)
   "</pre></div></body></html>"))

(defn wrap-yaml-in-html-response
  "Wrapper to serialize structures in :body to YAML wrapped in HTML to
  check things out in the browser. See wrap-format-response for more
  details."
  [handler & {:keys [predicate encoder type charset handle-error]
              :or {predicate serializable?
                   encoder wrap-yaml-in-html
                   type "text/html"
                   charset "utf-8"
                   handle-error default-handle-error}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoders [(make-encoder encoder type)]
                        :charset charset
                        :handle-error handle-error))

(def format-encoders
  {:json (make-encoder json/generate-string "application/json")
   :edn (make-encoder generate-native-clojure "application/edn")
   :clojure (make-encoder generate-native-clojure "application/clojure")
   :yaml (make-encoder yaml/generate-string "application/x-yaml")
   :yaml-in-html (make-encoder wrap-yaml-in-html "text/html")})

(defn wrap-restful-response
  "Wrapper that tries to do the right thing with the response :body
  and provide a solid basis for a RESTful API. It will serialize to
  JSON, YAML, Clojure or HTML-wrapped YAML depending on Accept header.
  See wrap-format-response for more details."
  [handler & {:keys [handle-error formats charset]
              :or {handle-error default-handle-error
                   charset "utf-8"
                   formats [:json :yaml :edn :clojure :yaml-in-html]}}]
  (let [encoders (for [format formats
                       :when format
                       :let [encoder (if (map? format)
                                       format
                                       (get format-encoders (keyword format)))]
                       :when encoder]
                   encoder)]
    (wrap-format-response handler
                          :predicate serializable?
                          :encoders encoders
                          :charset charset
                          :handle-error handle-error)))

(defn wrap-restful-response
  "Wrapper that tries to do the right thing with the response :body
  and provide a solid basis for a RESTful API. It will serialize to
  JSON, YAML, Clojure or HTML-wrapped YAML depending on Accept header.
  See wrap-format-response for more details."
  [handler & {:keys [handle-error formats charset]
              :or {handle-error default-handle-error
                   charset "utf-8"
                   formats [:json :yaml :edn :clojure :yaml-in-html]}}]
  (let [encoders (for [format formats
                       :when format
                       :let [encoder (if (map? format)
                                       format
                                       (get format-encoders (keyword format)))]
                       :when encoder]
                   encoder)]
    (wrap-format-response handler
                          :predicate serializable?
                          :encoders encoders
                          :charset charset
                          :handle-error handle-error)))