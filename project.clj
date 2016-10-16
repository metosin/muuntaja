(defproject metosin/muuntaja "0.1.0-SNAPSHOT"
  :description "Snappy lib for encoding/decoding http api formats"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[cheshire "5.6.3" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.4"]
                 [circleci/clj-yaml "0.5.5"]
                 [clojure-msgpack "1.2.0" :exclusions [org.clojure/clojure]]
                 [com.cognitect/transit-clj "0.8.290"]]
  :plugins [[lein-codox "0.10.1"]]
  :codox {:src-uri "http://github.com/metosin/muuntaja/blob/master/{filepath}#L{line}"
          :defaults {:doc/format :markdown}}
  :profiles {:dev {:jvm-opts ^:replace ["-server"]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [ring/ring-core "1.6.0-beta6"]
                                  [ring-middleware-format "0.7.0"]
                                  [ring-transit "0.1.6"]
                                  [ring/ring-json "0.4.0"]

                                  ;; Pedestal
                                  [io.pedestal/pedestal.service "0.5.1"]
                                  [javax.servlet/javax.servlet-api "3.1.0"]
                                  [org.slf4j/slf4j-log4j12 "1.7.12"]

                                  [criterium "0.4.4"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha12"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev:dev,1.9"]
            "perf" ["with-profile" "default,dev,perf"]
            "analyze" ["with-profile" "default,dev,analyze"]})
