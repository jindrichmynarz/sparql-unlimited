(ns sparql-unlimited.mustache
  (:require [sparql-unlimited.util :refer [join-lines]]
            [clojure.set :refer [difference]]
            [clojure.string :as string]
            [stencil.parser :refer [parse]]))

(defn get-template-variables
  "Get a set of variables used in `template`."
  [template]
  (->> (parse template)
       (filter (complement string?))
       (mapcat :name)
       distinct
       set))

(defn validate
  "Validate the correctness of Mustache syntax in `template`."
  [template]
  (try (parse template) nil
       (catch Exception e (join-lines ["The provided Mustache template is invalid:"
                                       (.getMessage e)]))))

(defn all-variables-provided?
  "Validates if `config` provides all variables used in `template`."
  [config template]
  (let [map-set-names (comp set (partial map name))
        template-variables (map-set-names (difference (get-template-variables template) #{:limit :offset}))
        provided-variables (map-set-names (keys (get config :params {})))
        missing-variables (difference template-variables provided-variables)]
    (when (seq missing-variables)
      (join-lines ["Configuration is missing these variables used in the template:"
                   (string/join ", " missing-variables)]))))
