(defproject ring-middleware-format "0.7.0-SNAPSHOT"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats (mainly JSON, YAML and Transit out of
  the box)"
  :url "https://github.com/ngrunwald/ring-middleware-format"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/core.memoize "0.5.7"]
                 [ring/ring-core "1.4.0"]
                 [cheshire "5.5.0"]
                 [org.clojure/tools.reader "0.10.0-alpha3"]
                 [com.ibm.icu/icu4j "55.1"]
                 [circleci/clj-yaml "0.5.3"]
                 [clojure-msgpack "1.1.1"]
                 [com.cognitect/transit-clj "0.8.281"]]
  :plugins [[codox "0.8.11"]]
  :codox {:src-dir-uri "http://github.com/ngrunwald/ring-middleware-format/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.6"]})
