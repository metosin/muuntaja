(ns muuntaja.util
  (:import (java.io InputStream ByteArrayOutputStream)))

(set! *warn-on-reflection* true)

(defn throw! [formats format message]
  (throw
    (ex-info
      (str message ": " format)
      {:formats (-> formats :formats keys)
       :format format})))

(defn some-value [pred c]
  (let [f (fn [x] (if (pred x) x))]
    (some f c)))

(defn assoc-assoc [m k1 k2 v]
  (assoc m k1 (assoc (k1 m) k2 v)))

(defn slurp-to-bytes ^bytes [^InputStream in]
  (if in
    (let [buf (byte-array 4096)
          out (ByteArrayOutputStream.)]
      (loop []
        (let [r (.read in buf)]
          (when (not= r -1)
            (.write out buf 0 r)
            (recur))))
      (.toByteArray out))))

(defmacro when-ns [ns & body]
  `(try
     (eval
       '(do
          (require ~ns)
          ~@body))
     (catch Exception ~'_)))
