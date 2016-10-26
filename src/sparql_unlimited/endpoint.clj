(ns sparql-unlimited.endpoint
  (:require [mount.core :as mount :refer [defstate]]
            [clj-http.client :as client]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.string :as string]))

(defn init-endpoint
  "Ping endpoint to test if it is up and if the authorization via `username` and `password` is working."
  [{:keys [sparql-endpoint username password]
    :as config}]
  (let [virtuoso? (-> sparql-endpoint
                      (client/head {:digest-auth [username password]
                                    :throw-entire-message? true})
                      (get-in [:headers "Server"])
                      (string/includes? "Virtuoso"))]
    (assoc config :virtuoso? virtuoso?)))

(defstate endpoint
  :start (init-endpoint (mount/args)))
