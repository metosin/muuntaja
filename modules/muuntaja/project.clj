(defproject metosin/muuntaja "0.6.6"
  :description "Clojure library for format encoding, decoding and content-negotiation"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/metosin/muuntaja"
        :dir "../.."}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories
                             :managed-dependencies
                             :profiles [:dev]]}
  :dependencies [[metosin/jsonista]
                 [ring/ring-codec]
                 [com.cognitect/transit-clj]])
