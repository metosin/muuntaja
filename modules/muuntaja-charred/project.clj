(defproject fi.metosin/muuntaja-charred "0.6.11"
  :description "Charred/JSON format for Muuntaja"
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
  :dependencies [[metosin/muuntaja]
                 [com.cnuernber/charred]])
