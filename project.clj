(defproject gor-middleware "0.2.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.6.3"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [commons-io/commons-io "2.4"]
                 ;; https://mvnrepository.com/artifact/commons-codec/commons-codec
                 [commons-codec/commons-codec "1.2"]
                 [clj-time "0.11.0"]]
  :main gor-middleware.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :aot [gor-middleware.core])
