(ns ring.middleware.format-response
  (:require [clj-json.core :as json]
            [ring.util.response :as res]
            [clojure.java.io :as io]
            [clj-yaml.core :as yaml])
  (:import [java.io File InputStream BufferedInputStream]))

(defn serializable?
  "Predicate that returns true whenever the response body is not a String, File or InputStream."
  [_ {:keys [body]}]
  (not (or
        (string? body)
        (instance? File body)
        (instance? InputStream body))))

(defn make-type-accepted-pred
  "Predicate that returns a predicate fn checking if Accept request header matches a specified regexp and the response body is serializable."
  [regexp]
  (fn [{:keys [headers] :as request} response]
    (if-let [#^String type (get headers "accept")]
      (and (serializable? request response) (not (empty? (re-find regexp type)))))))

(def json-accepted? (make-type-accepted-pred #"^application/(vnd.+)?json"))

(defn wrap-format-response
  "Wraps a handler such that responses body to requests are formatted to the right format.
:predicate is a predicate taking the request and response as arguments to test if serialization should be used.
:encoder specifies a fn taking the body as sole argument and giving back an encoded string.
:type allows to specify a Content-Type for the encoded string.
:charset can be either a string representing a valid charset or a fn taking the req as argument and returning a valid charset (utf-8 is strongly suggested)."
  [handler & {:keys [predicate encoder type charset]}]
  (fn [req]
    (let [{:keys [headers body] :as response} (handler req)]
      (if (predicate req response)
        (let [char-enc (if (string? charset) charset (charset req))
              body-string (encoder body)
              body* (.getBytes body-string char-enc)
              body-length (count body*)]
          (-> response
              (assoc :body (io/input-stream body*))
              (res/content-type (str type "; charset=" char-enc))
              (res/header "Content-Length" body-length)))
        response))))

(defn wrap-json-response
  "Wrapper to serialize structures in :body to JSON with sane defaults. See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset]
          :or {predicate serializable?
               encoder json/generate-string
               type "application/json"
               charset "utf-8"}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoder encoder
                        :type type
                        :charset charset))

;; Functions for Clojure native serialization

(defn- generate-native-clojure
  [struct]
  (pr-str struct))

(defn- generate-hf-clojure
  [struct]
  (binding [*print-dup* true]
    (pr-str struct)))

(def clojure-accepted? (make-type-accepted-pred #"^application/(vnd.+)?(x-)?clojure"))

(defn wrap-clojure-response
  "Wrapper to serialize structures in :body to Clojure native with sane defaults.
If :hf is set to true, will use *print-dup* for high-fidelity printing ( see https://groups.google.com/d/msg/clojure/5wRBTPNu8qo/1dJbtHX0G-IJ ).
See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset hf]
          :or {predicate serializable?
               encoder generate-native-clojure
               type "application/clojure"
               charset "utf-8"
               hf false}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoder (if hf generate-hf-clojure encoder)
                        :type type
                        :charset charset))

;; Functions for yaml

(def yaml-accepted? (make-type-accepted-pred #"^(application|text)/(vnd.+)?(x-)?yaml"))

(defn wrap-yaml-response
  "Wrapper to serialize structures in :body to YAML with sane defaults. See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset]
          :or {predicate serializable?
               encoder yaml/generate-string
               type "application/x-yaml"
               charset "utf-8"}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoder encoder
                        :type type
                        :charset charset))

(def html-accepted? (make-type-accepted-pred #"^text/(vnd.+)?html"))

(defn- wrap-yaml-in-html
  [body]
  (str
   "<html>\n<head></head>\n<body><div><pre>\n"
   (yaml/generate-string body)
   "</pre></div></body></html>"))

(defn wrap-yaml-in-html-response
  "Wrapper to serialize structures in :body to YAML wrapped in HTML to check things out in the browser. See wrap-format-response for more details."
  [handler & {:keys [predicate encoder type charset]
          :or {predicate html-accepted?
               encoder wrap-yaml-in-html
               type "text/html"
               charset "utf-8"}}]
  (wrap-format-response handler
                        :predicate predicate
                        :encoder encoder
                        :type type
                        :charset charset))

(defn wrap-restful-response
  "Wrapper that tries to do the right thing with the response :body and provide a solid basis for a RESTful API.
It will serialize to JSON, YAML, Clojure or HTML-wrapped YAML depending on Accept header.
It takes an optional :default parameter wich is a wrapper fn that is used last as a default (JSON by default). See wrap-format-response for more details."
  [handler & {:keys [default] :or {default wrap-json-response}}]
  (-> handler
      (wrap-json-response :predicate json-accepted?)
      (wrap-yaml-response :predicate yaml-accepted?)
      (wrap-clojure-response :predicate clojure-accepted?)
      (wrap-yaml-in-html-response)
      (default)))
