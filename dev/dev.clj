(ns dev
  (:require [clojure.tools.namespace.repl :as repl]))

(defn start []
  (println "Anything to start?"))

(defn stop []
  (println "Anything to stop?"))

(defn refresh []
  (repl/refresh :after 'dev/start))

(defn conversation 
  "Load the conversation example ns and switch to it"
  []
  (require 'examples.conversation)
  (in-ns 'examples.conversation))

(defn debate 
  "Load the debate example ns and switch to it"
  []
  (require 'examples.debate)
  (in-ns 'examples.debate))
