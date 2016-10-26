(ns sparql-unlimited.util
  (:require [clojure.string :as string]))

(def join-lines 
  (partial string/join \newline))
