(defproject metosin/muuntaja-multipart "0.6.0"
  :description "Multipart format for Muuntaja"
  :url "https://github.com/metosin/muuntaja"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-parent "0.3.2"]]
  :parent-project {:path "../../project.clj"
                   :inherit [:deploy-repositories :managed-dependencies]}
  :dependencies [[metosin/muuntaja]
                 [org.synchronoss.cloud/nio-multipart-parser]])

