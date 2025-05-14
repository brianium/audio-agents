(ns examples.conversation
  "An example of having an audible conversation with gpt in Clojure"
  (:require [ayyygents.workflow :refer [ayyygent io-chan dialogue]]
            [audio.microphone :refer [record]]
            [audio.playback :refer [playback]]
            [oai-clj.core :as openai]
            [clojure.core.async :as async :refer [<! go-loop chan]]
            [clojure.java.io :as io]
            [clojure.set :as set])
  (:import [java.io ByteArrayInputStream]))

;;; First: let's define a channel representing us. Audio goes in, user message comes out

(defn mic-chan
  "mic-chan (♥ω♥*)
   any input message to this channel will immediately start the mic and output will be sent when
   recording is finished per the rules of audio.microphone/record."
  []
  (let [in         (chan)
        audio-in   (chan)
        out        (chan)
        io         (io-chan in out)
        transcribe (fn [audio-bytes]
                     (let [input           (ByteArrayInputStream. audio-bytes)
                           random-filename (str (java.util.UUID/randomUUID) ".wav")
                           result (openai/transcribe :file {:value input :filename random-filename})]
                       {:role :user :content (get-in result [:transcription :text])}))]
    (async/pipeline-blocking 1 out (map transcribe) audio-in false)
    (go-loop []
      (let [msg (<! in)]
        (if (nil? msg)
          (async/close! io)
          (do
            (println "Recording")
            (record (fn [audio-bytes _ _]
                      (async/put! audio-in audio-bytes)))
            (recur)))))
    io))

;;; Next we define an agent for our conversation partner. The agent context will just be an atom with a vector of messages.

(defn conversation-partner
  "Prompt is text to be used for the system prompt. The persona prompt is optional and used to guide the conversation partner's personality.
   This example uses gpt-4o and context backed by an atom"
  [system-prompt & [persona-prompt xf]]
  (let [context    (atom (cond-> [{:role    :system
                                   :content system-prompt}]
                           (some? persona-prompt) (conj {:role    :user
                                                         :content persona-prompt})))
        gpt-4o (fn [*context input]
                 (let [log-entries  (swap! *context conj input)
                       response     (openai/create-response :easy-input-messages log-entries)
                       output-entry {:role    :assistant
                                     :content (-> (:output response) first :message :content first :output-text :text)
                                     :format  (when-some [fmt (some-> (:text response) :format)]
                                                (if (some? (:json-schema fmt))
                                                  :json-schema
                                                  :text))}
                       _ (println output-entry)]
                   (swap! *context conj output-entry)
                   output-entry))]
    (ayyygent context gpt-4o 1 xf)))

(defn with-speech
  "We must give our partner the gift of speech. Attaches a stop-playback function to the agent that
   can be used to immediately cut audio playback."
  [ayyygent & {:keys [voice instructions content-fn]
               :or   {voice :onyx content-fn :content}}]
  (let [out      (chan)
        io       (io-chan ayyygent out)
        *stop-fn (atom nil)
        speak    (fn [message result]
                   (let [resume-after-playback (fn []
                                                 (async/put! result message)
                                                 (async/close! result))
                         stop-fn               (-> (openai/create-speech :input (content-fn message) :voice voice :instructions instructions)
                                                   (playback :on-complete resume-after-playback))]
                     (reset! *stop-fn stop-fn)))]
    (async/pipeline-async 1 out speak ayyygent false)
    (assoc io :context (:context ayyygent) :mult (:mult ayyygent) :stop-playback (fn []
                                                                                   (when @*stop-fn
                                                                                     (@*stop-fn)
                                                                                     (reset! *stop-fn nil))))))

;;; Let's try a conversation between ourselves and the borg

(defn chat-with-gpt
  "Start a dialogue with gpt. Returns a channel with the context of the conversation partner. Closing
   the channel will stop audio playback immediately."
  [& {:keys [voice instructions prompt persona]}]
  (let [persona-prompt (slurp (io/resource (str "prompts/personas/" persona ".md")))
        partner        (-> (io/resource prompt)
                           (slurp)
                           (conversation-partner persona-prompt)
                           (with-speech :voice voice :instructions instructions))
        mic            (mic-chan)
        d              (dialogue mic partner (async/sliding-buffer 1))] ;;; We aren't really doing anything with the text output
    (go-loop []
      (let [msg (<! d)]
        (if (nil? msg)
          (do ((:stop-playback partner))
              (async/close! partner)
              (async/close! mic)
              (async/close! d))
          (recur))))
    (async/put! d "はじめ") ;; The input message just kicks things off - its contents are irrelevant
    (assoc d :context (:context partner))))

;;; Start a dialogue with your favorite persona - prompts are stored in resources/prompts/*
#_(def d (chat-with-gpt 
          :prompt "prompts/persona-base.md"
          :voice :onyx 
          :persona "mark-twain"
          :instructions "Speak with a deep, southern united states accent"))

;;; Take a look at the context log of your conversation partner
#_(:context d)

;;; Shut it down
#_(async/close! d)
