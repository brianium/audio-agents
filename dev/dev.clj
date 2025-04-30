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

(defn sketch-artist
  "An audio agent with a visual output modality. An audible sketch artist
  experience"
  []
  (require 'examples.visual)
  (in-ns 'examples.visual))
