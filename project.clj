(defproject metosin/muuntaja "0.6.7"
  :description "Clojure library for format encoding, decoding and content-negotiation"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v20.html"}
  :javac-options ["-Xlint:unchecked" "-target" "1.7" "-source" "1.7"]
  :java-source-paths ["src/java"]
  :managed-dependencies [[metosin/muuntaja "0.6.7"]
                         [ring/ring-codec "1.1.2"]
                         [metosin/jsonista "0.2.6"]
                         [com.cognitect/transit-clj "1.0.324"]
                         [cheshire "5.10.0"]
                         [clj-commons/clj-yaml "0.7.1"]
                         [metosin/jsonista "0.2.6"]
                         [com.cognitect/transit-clj "1.0.324"]
                         [cheshire "5.10.0"]
                         [clj-commons/clj-yaml "0.7.1"]
                         [clojure-msgpack "1.2.1" :exclusions [org.clojure/clojure]]]
  :dependencies []
  :source-paths ["modules/muuntaja/src"]
  :plugins [[lein-codox "0.10.7"]]
  :codox {:src-uri "http://github.com/metosin/muuntaja/blob/master/{filepath}#L{line}"
          :output-path "doc"
          :metadata {:doc/format :markdown}}
  :scm {:name "git"
        :url "https://github.com/metosin/muuntaja"}
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/muuntaja-cheshire/src"
                                  "modules/muuntaja-form/src"
                                  "modules/muuntaja-yaml/src"
                                  "modules/muuntaja-msgpack/src"]

                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [ring/ring-core "1.8.1"]
                                  [ring-middleware-format "0.7.4"]
                                  [ring-transit "0.1.6"]
                                  [ring/ring-json "0.5.0"]

                                  ;; modules
                                  [metosin/muuntaja "0.6.7"]
                                  [metosin/muuntaja-form "0.6.7"]
                                  [metosin/muuntaja-cheshire "0.6.7"]
                                  [metosin/muuntaja-msgpack "0.6.7"]
                                  [metosin/muuntaja-yaml "0.6.7"]

                                  ;; correct jackson
                                  [com.fasterxml.jackson.core/jackson-databind "2.11.0"]

                                  ;; Sieppari
                                  [metosin/sieppari "0.0.0-alpha5"]

                                  ;; Pedestal
                                  [org.clojure/core.async "1.1.587" :exclusions [org.clojure/tools.reader]]
                                  [io.pedestal/pedestal.service "0.5.7" :exclusions [org.clojure/tools.reader
                                                                                     org.clojure/core.async
                                                                                     org.clojure/core.memoize]]
                                  [javax.servlet/javax.servlet-api "4.0.1"]
                                  [org.slf4j/slf4j-log4j12 "1.7.30"]

                                  [criterium "0.4.5"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev:dev,1.7:dev,1.8:dev,1.9"]
            "perf" ["with-profile" "default,dev,perf"]
            "analyze" ["with-profile" "default,dev,analyze"]})
