(defproject muuntaja-dev "0.6.11"
  ;; See modules/muuntaja/project.clj for actual project.clj used when releasing.
  ;; This project.clj is just for local development.
  :description "Clojure library for format encoding, decoding and content-negotiation"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v20.html"}
  :managed-dependencies [[metosin/muuntaja "0.6.11"]
                         [ring/ring-codec "1.2.0"]
                         [metosin/jsonista "0.3.13"]
                         [com.cognitect/transit-clj "1.0.333"]
                         [com.cnuernber/charred "1.034"]
                         [cheshire "5.13.0"]
                         [clj-commons/clj-yaml "1.0.29"]
                         [clojure-msgpack "1.2.1" :exclusions [org.clojure/clojure]]]
  :deploy-repositories [["releases" {:url "https://repo.clojars.org/"
                                     :sign-releases false
                                     :username :env/CLOJARS_USER
                                     :password :env/CLOJARS_DEPLOY_TOKEN}]]
  :source-paths ["modules/muuntaja/src"]
  :plugins [[lein-codox "0.10.7"]
            [lein-ancient "1.0.0-RC3"]]
  :codox {:src-uri "http://github.com/metosin/muuntaja/blob/master/{filepath}#L{line}"
          :output-path "doc"
          :metadata {:doc/format :markdown}}
  :scm {:name "git"
        :url "https://github.com/metosin/muuntaja"}
  :profiles {:dev {:jvm-opts ^:replace ["-server"]

                   ;; all module sources for development
                   :source-paths ["modules/muuntaja-charred/src"
                                  "modules/muuntaja-cheshire/src"
                                  "modules/muuntaja-form/src"
                                  "modules/muuntaja-yaml/src"
                                  "modules/muuntaja-msgpack/src"
                                  "modules/muuntaja/src"]

                   :dependencies [[org.clojure/clojure "1.12.0"]
                                  [com.cnuernber/charred "1.034"]
                                  [ring/ring-core "1.13.0"]
                                  [ring-middleware-format "0.7.5"]
                                  [ring-transit "0.1.6"]
                                  [ring/ring-json "0.5.1"]
                                  [metosin/jsonista "0.3.13"]

                                  ;; correct jackson
                                  [com.fasterxml.jackson.core/jackson-databind "2.18.2"]

                                  ;; Sieppari
                                  [metosin/sieppari "0.0.0-alpha5"]

                                  ;; Pedestal
                                  [org.clojure/core.async "1.7.701" :exclusions [org.clojure/tools.reader]]
                                  [io.pedestal/pedestal.service "0.7.2" :exclusions [org.clojure/tools.reader
                                                                                     org.clojure/core.async
                                                                                     org.clojure/core.memoize]]
                                  [jakarta.servlet/jakarta.servlet-api "5.0.0"]
                                  [org.slf4j/slf4j-log4j12 "2.0.16"]

                                  [criterium "0.4.6"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.2"]]}
             :perf {:jvm-opts ^:replace ["-server"
                                         "-Xmx4096m"
                                         "-Dclojure.compiler.direct-linking=true"]}
             :analyze {:jvm-opts ^:replace ["-server"
                                            "-Dclojure.compiler.direct-linking=true"
                                            "-XX:+PrintCompilation"
                                            "-XX:+UnlockDiagnosticVMOptions"
                                            "-XX:+PrintInlining"]}}
  :aliases {"all" ["with-profile" "dev:dev,1.8:dev,1.9:dev,1.10"]
            "perf" ["with-profile" "default,dev,perf"]
            "analyze" ["with-profile" "default,dev,analyze"]})
