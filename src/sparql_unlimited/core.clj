(ns sparql-unlimited.core
  (:gen-class)
  (:require [sparql-unlimited.mustache :as mustache]
            [sparql-unlimited.util :refer [join-lines]]
            [sparql-unlimited.sparql :as sparql]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :refer [as-file]]
            [slingshot.slingshot :refer [try+]]
            [clojure.edn :as edn]
            [schema.core :as s]
            [mount.core :as mount])
  (:import (org.apache.commons.validator.routines UrlValidator)))

; ----- Schemata -----

(def ^:private positive-number (s/constrained s/Int pos? 'pos?))

(def ^:private http? (partial re-matches #"^https?:\/\/.*$"))

(def ^:private valid-url?
  "Test if `url` is valid."
  (let [validator (UrlValidator. UrlValidator/ALLOW_LOCAL_URLS)]
    (fn [url]
      (.isValid validator url))))

(def ^:private url
  (s/pred valid-url? 'valid-url?))

(def ^:private Config
  {:sparql-endpoint (s/conditional http? url) ; The URL of the SPARQL endpoint.
   :username s/Str ; Credentials of a user permitted to execute SPARQL Update operations.
   :password s/Str
   (s/optional-key :page-size) positive-number
   ; Maximum execution time of the SPARQL operaration in milliseconds (defaults to 10 minutes).
   (s/optional-key :timeout) positive-number
   ; Maximum number of attempts until an exception is thrown.
   (s/optional-key :max-attempts) positive-number
   (s/optional-key :default-graph) url ; An optional parameter specifying default named graph. 
   (s/optional-key :params) {(s/cond-pre s/Keyword s/Str) s/Str}
   (s/optional-key :start-from) positive-number})

; ----- Private functions -----

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (join-lines errors)))

(defn- exit
  "Exit with @status and message `msg`.
  `status` 0 is OK, `status` 1 indicates error."
  [^Integer status
   ^String msg]
  {:pre [(#{0 1} status)]}
  (println msg)
  (System/exit status))

(def ^:private die
  (partial exit 1))

(def ^:private info
  (partial exit 0))

(defn- file-exists?
  "Test if file at `path` exists and is a file."
  [path]
  (let [file (as-file path)]
    (and (.exists file) (.isFile file))))

(defn- usage
  "Wrap usage `summary` in a description of the program."
  [summary]
  (join-lines ["Execute unlimited SPARQL!"
               "Options:\n"
               summary]))

(def ^:private validate-config
  "Validate configuration `config` according to its schema."
  (let [expected-structure (s/explain Config)]
    (fn [config]
      (try (s/validate Config config) nil
           (catch RuntimeException e (join-lines ["Invalid configuration:"
                                                  (.getMessage e)
                                                  "The expected structure of configuration is:"
                                                  expected-structure]))))))

; ----- Private vars -----

(def ^:private cli-options
  [["-c" "--config CONFIG" "Path to configuration file in EDN"
    :parse-fn (comp edn/read-string slurp)]
   ["-s" "--sparql SPARQL" "Path to SPARQL file"
    :validate [file-exists? "The SPARQL file doesn't exist!"]]
   ["-v" "--verbose" "Print verbose information"]
   ["-h" "--help" "Display help message"]])

(defn- die-of-incomplete-results
  [{[max-attempts] :keys}]
  (-> "SPARQL endpoint returned incomplete results after exceeding maximum of %d attempts."
      (format max-attempts)
      die))

(defn main
  [config sparql-string]
  (try+ (mount/start-with-args config)
        (catch [:status 401] _
          (die "Invalid username or password!")))
  (try+ (sparql/execute sparql-string)
        (catch [:type :sparql/incomplete-results] _
          (die-of-incomplete-results config))
        (catch [:status 500] {:keys [body]}
          (die body))))

; ----- Public functions -----

(defn -main
  [& args]
  (let [{{:keys [config help sparql verbose]} :options
         :keys [errors summary]} (parse-opts args cli-options)
        ; Merge defaults
        config' (merge {:max-attempts 5 :page-size 10000} config)]
    (cond help (info (usage summary)) 
          errors (die (error-msg errors))
          :else (let [sparql-string (slurp sparql)]
                  (if-let [error (or (validate-config config')
                                     (mustache/validate sparql)
                                     (mustache/all-variables-provided? config' sparql-string))]
                    (die error)
                    (main (assoc config' :verbose verbose) sparql-string))))))
