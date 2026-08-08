(defproject de.dixieflatline/persona "0.1.0-SNAPSHOT"
  :description "A lightweight hierarchical authorization library"
  :url "https://github.com/20centaurifux/persona"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [de.dixieflatline/smuk "0.1.0-SNAPSHOT"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})