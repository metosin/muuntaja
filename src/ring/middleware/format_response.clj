(ns ring.middleware.format-response
  (:require [clj-json.core :as json]
            [ring.util.response :as res]
            [clojure.java.io :as io]))

(defn make-type-accepted-pred
  "Predicate that returns a predicate fn checking if Accept request header matches a specified regexp."
  [regexp]
  (fn [req]
    (if-let [#^String type (:accept req)]
      (not (empty? (re-find regexp type))))))

(def json-accepted? (make-type-accepted-pred #"^application/(vnd.+)?json"))

(defn serializable?
  "Predicate that returns true whenever the response body is not a String."
  [req]
  (not (string? (:body req))))

(defn wrap-format-response
  "Wraps an app such that responses body to requests with the right Content-Accepted header are formatted to json (default behaviour).
:predicate is a predicate taking the request as sole argument to test if serialization should be used.
:encoder specifies a fn taking the body as sole argument and giving back an encoded string.
:type allows to specify a Content-Type for the encoded string.
:charset can be either a string representing a valid charset or a fn taking the req as argument and returning a valid charset (defaults to utf-8)."
  [app & {:keys [predicate encoder type charset]
          :or {predicate serializable?
               encoder json/generate-string
               type "application/json"
               charset "utf-8"}}]
  (fn [req]
    (let [{:keys [headers body] :as response} (app req)]
      (if (apply predicate [req])
        (let [char-enc (if (string? charset) charset (apply charset [req]))
              body-string (apply encoder [body])
              body* (.getBytes body-string char-enc)
              body-length (count body*)]
          (-> response
              (assoc :body (io/input-stream body*))
              (res/content-type (str type "; charset=" char-enc))
              (res/header "content-length" body-length)))
        response))))
