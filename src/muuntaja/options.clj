(ns muuntaja.options
  (:require [clojure.set :as set]
            [muuntaja.core :as m]))

(defn transform-format-options [f options]
  (update options :formats #(into (empty %) (map (fn [[k v]] [k (f v)]) %))))

(def with-no-decoding (partial transform-format-options #(dissoc % :decoder)))
(def with-no-encoding (partial transform-format-options #(dissoc % :encoder)))

(def no-protocol-encoding
  (partial transform-format-options #(dissoc % :encode-protocol)))

(defn with-decoder-opts [options format opts]
  (when-not (get-in options [:formats format])
    (throw
      (ex-info
        (str "invalid format: " format)
        {:format format
         :formats (keys (:formats options))})))
  (assoc-in options [:formats format :decoder-opts] opts))

(defn with-encoder-opts [options format opts]
  (when-not (get-in options [:formats format])
    (throw
      (ex-info
        (str "invalid format: " format)
        {:format format
         :formats (keys (:formats options))})))
  (assoc-in options [:formats format :encoder-opts] opts))

(defn with-formats [options formats]
  (let [existing-formats (-> options :formats keys set)
        future-formats (set formats)]
    (when-let [diff (seq (set/difference future-formats existing-formats))]
      (throw
        (ex-info
          (str "invalid formats: " diff)
          {:invalid (seq diff)
           :formats (seq formats)
           :existing (seq existing-formats)})))
    (-> options
        (update :formats select-keys formats)
        (assoc :default-format (first formats)))))

;;
;; Legacy matchers
;;

(def default-options-with-format-regexps
  (-> m/default-options
      (assoc-in [:formats "application/json" :matches] #"^application/(.+\+)?json$")
      (assoc-in [:formats "application/edn" :matches] #"^application/(vnd.+)?(x-)?(clojure|edn)$")
      (assoc-in [:formats "application/msgpack" :matches] #"^application/(vnd.+)?(x-)?msgpack$")
      (assoc-in [:formats "application/x-yaml" :matches] #"^(application|text)/(vnd.+)?(x-)?yaml$")
      (assoc-in [:formats "application/transit+json" :matches] #"^application/(vnd.+)?(x-)?transit\+json$")
      (assoc-in [:formats "application/transit+msgpack" :matches] #"^application/(vnd.+)?(x-)?transit\+msgpack$")))
