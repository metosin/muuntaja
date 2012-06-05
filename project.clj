(defproject ring-middleware-format "0.2.0"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats. See
  https://github.com/ngrunwald/ring-middleware-format"
  :url "https://github.com/ngrunwald/ring-middleware-format"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/core.memoize "0.5.1"]
                 [ring/ring-core "1.0.2"]
                 [cheshire "4.0.0"]
                 [clj-yaml "0.3.1"]])
