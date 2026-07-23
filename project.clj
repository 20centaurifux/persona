(defproject de.dixieflatline/persona "0.1.0-SNAPSHOT"
  :description "A lightweight hierarchical authorization library"
  :url "https://github.com/20centaurifux/persona"
  :license {:name "AGPLv3.0"
            :url "https://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [de.dixieflatline/smuk "0.1.0-SNAPSHOT"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})