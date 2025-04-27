(ns ayyygents.workflow
  "🤙🏻 🤙🏻 🤙🏻 ayyyyyy gents its agents 🏄🏻‍♂️ - utilities for working with agents as channels"
  (:require [clojure.core.async :as async :refer [chan go-loop <! >! <!! >!!]]
            [clojure.core.async.impl.protocols :as proto :refer [ReadPort WritePort Channel]]))

;;; Async

(defrecord IoChannel [in out]
  ReadPort
  (take! [_ fn1] (proto/take! out fn1))
  WritePort
  (put! [_ val fn1] (proto/put! in val fn1))
  Channel
  (close! [_]
    (proto/close! in)
    (proto/close! out))
  (closed? [_] (proto/closed? in)))

(defn io-chan
  "An io-chan works like a single channel but gathers input on a separate channel
   from the output. A generic utility for separating input and output streams."
  [in out]
  (IoChannel. in out))

;;; Agents

(defrecord Ayyygent [context io mult]
  ReadPort
  (take! [_ fn1] (proto/take! io fn1))
  WritePort
  (put! [_ val fn1] (proto/put! io val fn1))
  Channel
  (close! [_] (proto/close! io))
  (closed? [_] (proto/closed? io)))

(defn ayyygent
  "An agent is a channel with context. Input and output streams are separated. The agent's :mult value
   can be used to tap into the output stream of the agent. (transition context input) will be called in a separate thread
   and the results will be put on the agent's output channel."
  ([context transition]
   (ayyygent context transition nil))
  ([context transition ex-handler]
   (let [in-chan       (chan)
         out-chan      (chan)
         mult          (async/mult out-chan)
         text-out      (chan)
         _             (async/tap mult text-out)
         io            (io-chan in-chan text-out)]
     (async/pipeline-blocking 1 out-chan (map (partial transition context)) in-chan false ex-handler)
     (Ayyygent. context io mult))))

(defn ayyygent?
  "Is this an ayyygent?"
  [x]
  (instance? Ayyygent x))

(defn get-context
  "Get the context of an ayyygent"
  [ayyygent]
  (:context ayyygent))

(defn tap
  "Convenient access to the ayyygent's underlying mult"
  ([ayyygent]
   (tap ayyygent (chan)))
  ([ayyygent ch]
   (tap ayyygent ch true))
  ([ayyygent ch close?]
   (let [mult (:mult ayyygent)]
     (async/tap mult ch close?)
     ch)))

(defn flow
  "A channel that passes previous output as input to the next channel in sequence. The optional transducer will be
   applied to EACH output value in the sequence."
  [chs & [xf ex-handler]]
  (let [in        (chan)
        out       (chan)
        io        (io-chan in out)
        xform     (or xf (map identity))
        result-ch (chan 1 xform ex-handler)]
    (go-loop [read in
              cs   (vec chs)]
      (let [v (<! read)]
        (if (nil? v)
          (async/close! result-ch)
          (if-some [ch (first cs)]
            (do (>!! result-ch v)
                (async/put! ch (<!! result-ch))
                (recur ch (rest cs)))
            (do (async/put! out v)
                (recur in (vec chs)))))))
    io))

(defn ordered-merge
  "A merge channel that ensures the output is in the order of the input channels"
  [chs & [xf]]
  (let [out (if xf
              (chan (count chs) xf)
              (chan (count chs)))]
    (go-loop [cs (vec chs)]
      (if (pos? (count cs))
        (let [v (<! (first cs))]
          (if (nil? v)
            (async/close! out)
            (do (>! out v)
                (recur (rest cs)))))
        (recur (vec chs))))
    out))

(defn fanout
  "A channel that takes a value and puts it on all channels in the sequence. The optional transducer will be
   applied to EACH output value in the sequence. The transducer will be applied via a pipeline-blocking operation.
   output will be sent as an ordered vector of each channel's output."
  [chs & [xf ex-handler]]
  (let [in        (chan)
        out       (chan)
        io        (io-chan in out)
        n         (count chs)
        merge-ch  (ordered-merge chs)
        agg-ch    (chan n)
        broadcast (fn [v]
                    (doseq [ch chs]
                      (async/put! ch v)))
        aggregate (fn []
                    (go-loop [items []]
                      (if (= (count items) n)
                        (async/put! out items)
                        (recur (conj items (<! agg-ch))))))]
    (if xf
      (async/pipeline-blocking n agg-ch xf merge-ch false ex-handler)
      (async/pipe merge-ch agg-ch false))
    (go-loop []
      (if-some [v (<! in)]
        (do (broadcast v)
            (<! (aggregate))
            (recur))
        (do (async/close! merge-ch)
            (async/close! agg-ch))))
    io))

(defn gate
  "Returns a channel that will release the value of ch when the gate receives any input. The output produced
   by a gate is a tuple containing the original input and the value of ch. xf and exception-handler are optional
   and follow normal chan semantics. A gate is useful for plugging a channel into a flow (potentially with some transformation). Also
   useful for forwarding messages through a sequence of channels."
  [ch & [xf ex-handler]]
  (let [in  (chan)
        out (chan 1 xf ex-handler)
        io  (io-chan in out)]
    (go-loop []
      (when-some [v (<! in)]
        (async/put! out [v (<! ch)])
        (recur)))
    io))
