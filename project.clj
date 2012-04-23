(defproject ring-middleware-format "0.2.0-SNAPSHOT"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats. See
  https://github.com/ngrunwald/ring-middleware-format"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [ring/ring-core "1.0.2"]
                 [cheshire "4.0.0"]
                 [clj-yaml "0.3.1"]])