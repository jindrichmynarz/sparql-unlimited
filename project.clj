(defproject sparql-unlimited "0.5.0"
  :description "Execute paged SPARQL updates from Mustache templates"
  :url "https://github.com/jindrichmynarz/sparql-unlimited"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/data.zip "0.1.2"]
                 [stencil "0.5.0"]
                 [clj-http "3.3.0"]
                 [prismatic/schema "1.1.3"]
                 [commons-validator/commons-validator "1.5.1"]
                 [slingshot "0.12.2"]
                 [mount "0.1.10"]]
  :main sparql-unlimited.core
  :profiles {:uberjar {:aot :all
                       :uberjar-name "sparql_unlimited.jar"}})
