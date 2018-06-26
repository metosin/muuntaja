(ns muuntaja.util
  (:import (java.io ByteArrayInputStream)))

(defn byte-stream [^bytes bytes]
  (ByteArrayInputStream. bytes))

(defn throw! [formats format message]
  (throw
    (ex-info
      (str message " " (pr-str format))
      {:formats (-> formats :formats keys)
       :format format})))

(defn some-value [pred c]
  (let [f (fn [x] (if (pred x) x))]
    (some f c)))

(defn assoc-assoc [m k1 k2 v]
  (assoc m k1 (assoc (k1 m) k2 v)))

(defmacro when-ns [ns & body]
  `(try
     (eval '(do (require ~ns) ~@body))
     (catch Exception ~'_)))
