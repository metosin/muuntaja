(ns muuntaja.protocols-test
  (:require [clojure.test :refer :all]
            [muuntaja.protocols :as protocols])
  (:import (java.io ByteArrayOutputStream)))

(deftest StreamableResponse-test
  (let [sr (protocols/->StreamableResponse #(.write % (.getBytes "kikka")))]
    (is (= "kikka" (slurp sr)))
    (is (= "kikka" (slurp (protocols/into-input-stream sr))))
    (is (= "kikka" (str (sr (ByteArrayOutputStream. 4096)))))))
