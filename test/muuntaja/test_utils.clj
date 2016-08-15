(ns muuntaja.test_utils
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn request-stream
  ([request]
   (request-stream request 3000000))
  ([request count]
   (let [i (atom 0)
         data (mapv
                (fn [_]
                  (->
                    request
                    (update :body #(ByteArrayInputStream. (.getBytes ^String %)))))
                (range count))]
     (fn []
       (let [item (nth data @i)]
         (swap! i inc)
         item)))))

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))
