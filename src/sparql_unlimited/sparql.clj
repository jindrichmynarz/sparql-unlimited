(ns sparql-unlimited.sparql
  (:require [sparql-unlimited.mustache :refer [get-template-variables]]
            [sparql-unlimited.endpoint :refer [endpoint]]
            [clj-http.client :as client]
            [stencil.core :refer [render-string]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.string :as string]))

; ----- Private functions -----

(defn- is-paged?
  "Test if `sparql` is a template for paged SPARQL operation.
  Paged SPARQL operations must use the variable named 'limit'." 
  [sparql]
  (:limit (get-template-variables sparql)))

(defn- xml->zipper
  "Take XML string `s`, parse it, and return XML zipper."
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

(defn- prefix-virtuoso-operation
  "Prefix `sparql-string` for Virtuoso if `virtuoso?` is true."
  [^String sparql-string
   ^Boolean virtuoso?]
  (if virtuoso?
    (str "DEFINE sql:log-enable 2\n" sparql-string)
    sparql-string))

(defn- execute-update
  "Execute a SPARQL Update operation.
  `sparql-string` is the SPARQL Update operation.
  `attempts` is the number of attempts the SPARQL operation was tried and failed."
  [^String sparql-string
   & {:keys [attempts]
      :or {attempts 0}}]
  (let [{:keys [default-graph max-attempts password sparql-endpoint timeout username verbose]
         :or {max-attempts 0}} endpoint
        form-params (cond-> {"timeout" timeout
                             "update" sparql-string}
                      default-graph (assoc :default-graph-uri default-graph))]
    (try+ (let [response (client/post sparql-endpoint {:digest-auth [username password]
                                                       :form-params form-params
                                                       :throw-entire-message? true})]
            (when verbose
              (println sparql-string)
              (println (format "Executed in %.2f seconds."
                               (-> response
                                   :request-time
                                   (/ 1000)
                                   double))))
            ; Throw an exception if Virtuoso indicates incomplete results
            (if (= (get-in response [:headers "X-SQL-State"]) "S1TAT")
              (if (= attempts max-attempts)
                (throw+ {:type ::incomplete-results})
                (do (println "Received partial results. Retrying...")
                  (execute-update sparql-string :attempts (inc attempts))))
              (:body response)))
          (catch Exception {:keys [body]}
            (println body)
            (throw+)))))

(defn- execute-unlimited-update
  [sparql-template]
  (let [{:keys [page-size params start-from verbose virtuoso?]
         :or {start-from 0}} endpoint
        message-regex (re-pattern #"(\d+)( \(or less\))?")
        update-fn (fn [offset]
                    (let [sparql (-> sparql-template
                                     (render-string (merge {:limit page-size
                                                            :offset offset} params))
                                     (prefix-virtuoso-operation virtuoso?))]
                      (when verbose (println (format "Executing update operation with offset %s..." offset)))
                      (execute-update sparql)))
        continue? (comp not
                        zero?
                        (fn [number-like]
                          (Integer/parseInt number-like))
                        second
                        (fn [message]
                          (re-find message-regex message))
                        first
                        (fn [zipper]
                          (zip-xml/xml-> zipper :results :result :binding :literal zip-xml/text))
                        xml->zipper)]
    (dorun (->> (iterate (partial + page-size) start-from)
                (map update-fn)
                (take-while continue?)))))

; ----- Public functions -----

(defn execute
  "Execute SPARQL from `sparql` template." 
  [sparql]
  (let [{:keys [params virtuoso?]} endpoint]
    (if (is-paged? sparql)
      (execute-unlimited-update sparql)
      (execute-update (-> sparql
                          (render-string params)
                          (prefix-virtuoso-operation virtuoso?))))))
