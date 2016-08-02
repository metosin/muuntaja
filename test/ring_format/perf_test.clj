(ns ring-format.perf-test
  (:require [criterium.core :as cc]
            [ring-format.core :as rfc]))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(set! *warn-on-reflection* true)

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(def +json-request+
  {:headers {"content-type" "application/json"}
   :body "kikka"})

(def +transit-json-request+
  {:headers {"content-type" "application/transit+json"}
   :body "kikka"})

;;
;; naive
;;

(defn old []

  (let [match? (fn [regexp]
                 (fn [{:keys [body] :as req}]
                   (if-let [^String type (get req :content-type
                                              (get-in req [:headers "Content-Type"]
                                                      (get-in req [:headers "content-type"])))]
                     (and body (not (empty? (re-find regexp type)))))))
        api-request? (some-fn
                       (match? #"^application/(vnd.+)?json")
                       (match? #"^application/(vnd.+)?(x-)?(clojure|edn)")
                       (match? #"^application/(vnd.+)?(x-)?msgpack")
                       (match? #"^(application|text)/(vnd.+)?(x-)?yaml")
                       (match? #"^application/(vnd.+)?(x-)?transit\+json")
                       (match? #"^application/(vnd.+)?(x-)?transit\+msgpack"))]

    ;; 551ns
    (title "R-M-F: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 2520ns
    (title "R-M-F: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+)))

  (let [match? (fn [type]
                 (fn [req] (and (= (get-in req [:headers "content-type"]) type)
                                (:body req)
                                true)))
        api-request? (some-fn
                       (match? "application/json")
                       (match? "application/edn")
                       (match? "application/msgpack")
                       (match? "application/yaml")
                       (match? "application/transit+json")
                       (match? "application/transi+msgpack"))]

    ;; 93ns
    (title "NAIVE: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 665ns
    (title "NAIVE: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+))))

;;
;; Real
;;

(defn core []
  ; 52ns
  (let [{:keys [extract-content-type-fn formats]} rfc/default-options
        {:keys [lookup matchers]} (rfc/parse-formats formats)]

    ; 52ns
    (title "NEW: JSON")
    (assert (= :json (rfc/extract-format lookup matchers extract-content-type-fn +json-request+)))
    (cc/quick-bench
      (rfc/extract-format lookup matchers extract-content-type-fn +json-request+))

    ; 65ns
    (title "NEW: TRANSIT")
    (assert (= :transit-json (rfc/extract-format lookup matchers extract-content-type-fn +transit-json-request+)))
    (cc/quick-bench
      (rfc/extract-format lookup matchers extract-content-type-fn +transit-json-request+))))

(defn all []
  (old)
  (core))

(comment
  (all))
