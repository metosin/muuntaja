(defproject ring-middleware-format "0.3.1"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats."
  :url "https://github.com/ngrunwald/ring-middleware-format"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.memoize "0.5.6"]
                 [ring "1.2.0"]
                 [cheshire "5.2.0"]
                 [org.clojure/tools.reader "0.7.6"]
                 [com.ibm.icu/icu4j "51.2"]
                 [clj-yaml "0.4.0"]])
