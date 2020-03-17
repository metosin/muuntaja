(defproject metosin/muuntaja "0.0.0"                        ;; use lein v
  :description "Clojure library for format encoding, decoding and content-negotiation"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/muuntaja"
        :dir "../.."}
  :plugins [[lein-parent "0.3.2"]
            [com.roomkey/lein-v "7.0.0"]]
  :middleware [leiningen.v/version-from-scm
               leiningen.v/dependency-version-from-scm
               leiningen.v/add-workspace-data]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories]}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [metosin/jsonista "0.2.5"]
                 [com.cognitect/transit-clj "0.8.319"]])
