(ns muuntaja.test_utils
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn request-stream [request]
  (let [b (.getBytes ^String (:body request))]
    (fn []
      (assoc request :body (ByteArrayInputStream. b)))))

(defn context-stream [request]
  (let [b (.getBytes ^String (:body request))
        ctx {:request request}]
    (fn []
      (assoc-in ctx [:request :body] (ByteArrayInputStream. b)))))

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))
