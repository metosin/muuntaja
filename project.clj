(defproject ring-middleware-format "0.4.0-SNAPSHOT"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats (mainly JSON, YAML and Transit out of
  the box)"
  :url "https://github.com/ngrunwald/ring-middleware-format"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.memoize "0.5.6"]
                 [ring "1.3.0"]
                 [cheshire "5.3.1"]
                 [org.clojure/tools.reader "0.8.5"]
                 [com.ibm.icu/icu4j "53.1"]
                 [clj-yaml "0.4.0"]
                 [com.cognitect/transit-clj "0.8.247"]]
  :plugins [[codox "0.8.10"]]
  :codox {:src-dir-uri "http://github.com/ngrunwald/ring-middleware-format/blob/master/"
          :src-linenum-anchor-prefix "L"
          :defaults {:doc/format :markdown}})
