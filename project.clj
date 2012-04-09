(defproject ring-middleware-format "0.2.0-SNAPSHOT"
  :description "Ring middleware for parsing parameters and emitting
  responses in various formats. See
  https://github.com/ngrunwald/ring-middleware-format"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring/ring-core "[0.3.11,)"]
                 [cheshire "2.0.4"]
                 [gfrlog/clj-yaml "0.3.0"]])
