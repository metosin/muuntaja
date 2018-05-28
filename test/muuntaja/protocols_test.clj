(ns muuntaja.protocols-test
  (:require [clojure.test :refer :all]
            [muuntaja.protocols :as protocols])
  (:import (java.io ByteArrayOutputStream)))

(deftest ByteResponse-test
  (let [br (protocols/->ByteResponse (.getBytes "kikka"))]
    (is (= "kikka" (slurp br)))
    (is (= "kikka" (slurp (protocols/-input-stream br))))))

(deftest StreamableResponse-test
  (let [sr (protocols/->StreamableResponse #(.write % (.getBytes "kikka")))]
    (is (= "kikka" (slurp sr)))
    (is (= "kikka" (slurp (protocols/-input-stream sr))))
    (is (= "kikka" (str (sr (ByteArrayOutputStream. 4096)))))))
