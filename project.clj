(defproject metosin/muuntaja "0.1.0-SNAPSHOT"
  :description "Snappy lib for encoding/decoding http api formats"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[ring/ring-core "1.5.0" :exclusions [commons-codec]]
                 [cheshire "5.6.3" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.3"]
                 [circleci/clj-yaml "0.5.5"]
                 [clojure-msgpack "1.2.0" :exclusions [org.clojure/clojure]]
                 [com.cognitect/transit-clj "0.8.288"]]
  :plugins [[lein-codox "0.9.5"]]
  :codox {:src-uri "http://github.com/metosin/muuntaja/blob/master/{filepath}#L{line}"
          :defaults {:doc/format :markdown}}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [ring-middleware-format "0.7.0"]
                                  [com.ibm.icu/icu4j "57.1"]
                                  [org.clojure/core.memoize "0.5.9"]
                                  [org.clojure/tools.reader "0.10.0"]
                                  [ring/ring-json "0.4.0"]
                                  [criterium "0.4.4"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha11"]]}
             :perf {:jvm-opts ^:replace ["-server"]}}
  :aliases {"all" ["with-profile" "dev:dev,1.9"]
            "perf" ["with-profile" "default,dev,perf"]})
