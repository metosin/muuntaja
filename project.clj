(defproject ring-middleware-format "0.7.1-SNAPSHOT"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats (mainly JSON, YAML and Transit out of
  the box)"
  :url "https://github.com/ngrunwald/ring-middleware-format"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/core.memoize "0.5.9"]
                 [ring/ring-core "1.5.0"]
                 [cheshire "5.6.3"]
                 [org.clojure/tools.reader "0.10.0"]
                 [com.ibm.icu/icu4j "57.1"]
                 [circleci/clj-yaml "0.5.5"]
                 [clojure-msgpack "1.2.0"]
                 [com.cognitect/transit-clj "0.8.288"]]
  :plugins [[lein-codox "0.9.5"]]
  :codox {:src-uri "http://github.com/ngrunwald/ring-middleware-format/blob/master/{filepath}#L{line}"
          :defaults {:doc/format :markdown}}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [criterium "0.4.4"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :perf {:jvm-opts ^:replace []}}
  :aliases {"all" ["with-profile" "dev:dev,1.6:dev,1.7"]
            "perf" ["with-profile" "default,dev,perf"]})
