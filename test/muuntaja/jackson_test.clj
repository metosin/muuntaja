(ns muuntaja.jackson-test
  (:require [clojure.test :refer :all]
            [muuntaja.jackson :as jackson]
            [cheshire.core :as cheshire]
            [cheshire.generate]
            [cheshire.generate :as generate])
  (:import (java.util UUID Date)
           (java.sql Timestamp)
           (com.fasterxml.jackson.core JsonGenerator)))

(deftest options-tests
  (is (= {"hello" "world"}
         (jackson/from-json
           (jackson/to-json {"hello" "world"}))))
  #_(is (= {:hello "world"}
           (jackson/from-json
             (jackson/to-json {"hello" "world"})
             {:key-fn true}))))

(deftest roundrobin-tests
  (let [data {:numbers {:integer (int 1)
                        :long (long 2)
                        :double (double 1.2)
                        :float (float 3.14)
                        :big-integer (biginteger 3)
                        :big-decimal (bigdec 4)
                        :ratio 3/4
                        :short (short 5)
                        :byte (byte 6)
                        :big-int (bigint 7)}
              :boolean true
              :string "string"
              :character \c
              :keyword :keyword
              :set #{1 2 3}
              :bytes (.getBytes "bytes")
              :uuid (UUID/fromString "fbe5a1e8-6c91-42f6-8147-6cde3188fd25")
              :symbol 'symbol
              :date (Date. 0)
              :timestamp (Timestamp. 0)}
        expected {:numbers {:integer 1
                            :long 2
                            :double 1.2
                            :float 3.14
                            :big-integer 3
                            :big-decimal 4
                            :ratio 0.75
                            :short 5
                            :byte 6
                            :big-int 7}
                  :boolean true
                  :string "string"
                  :character "c"
                  :keyword "keyword"
                  :set [1 3 2]
                  :bytes "Ynl0ZXM="
                  :uuid "fbe5a1e8-6c91-42f6-8147-6cde3188fd25"
                  :symbol "symbol"
                  :date "1970-01-01T00:00:00Z"
                  :timestamp "1970-01-01T00:00:00Z"}]

    (testing "cheshire"
      (is (= expected (cheshire/parse-string
                        (cheshire/generate-string data)
                        true))))

    #_(testing "jackson"
        (is (= (cheshire/generate-string data)
               (jackson/to-json data)))
        (is (= expected (jackson/from-json
                          (jackson/to-json data)
                          {:key-fn true}))))))

(defrecord StringLike [value])

(generate/add-encoder
  StringLike
  (fn [x ^JsonGenerator jg]
    (.writeString jg (str (:value x)))))

(deftest custom-encoders
  (let [data {:like (StringLike. "boss")}
        expected {:like "boss"}]

    (testing "cheshire"
      (is (= expected
             (cheshire/parse-string
               (cheshire/generate-string data)
               true))))

    #_(testing "jackson"
        (is (= (cheshire/generate-string data)
               (jackson/to-json data)))
        (is (= expected
               (jackson/from-json
                 (jackson/to-json data)
                 {:key-fn true}))))))
