(ns openai.core
  "Support some Clojure sugar for OpenAI's  Java Client"
  (:require [clojure.walk :as walk]
            [malli.json-schema :as mj])
  (:import [java.util ArrayList]
           [com.openai.client.okhttp OpenAIOkHttpClient]
           [com.openai.core JsonValue MultipartField]
           [com.openai.models ChatModel]
           [com.openai.models.audio AudioModel]
           [com.openai.models.audio.speech SpeechModel SpeechCreateParams SpeechCreateParams$Voice SpeechCreateParams$ResponseFormat]
           [com.openai.models.audio.transcriptions TranscriptionCreateParams]
           [com.openai.models.images ImageGenerateParams ImageGenerateParams$Size ImageModel]
           [com.openai.models.responses EasyInputMessage EasyInputMessage$Role ResponseCreateParams Response ResponseFormatTextJsonSchemaConfig ResponseFormatTextJsonSchemaConfig$Schema ResponseTextConfig]))

(def *client
  (delay
    (OpenAIOkHttpClient/fromEnv)))

(def roles
  {:user EasyInputMessage$Role/USER
   :assistant EasyInputMessage$Role/ASSISTANT
   :system EasyInputMessage$Role/SYSTEM})

(defn easy-input-message
  "All entries are assumed to be maps with :role and :content keys. role is a keyword 
   and content is a string"
  [{:keys [role content]}]
  (.build
   (doto (EasyInputMessage/builder)
     (.role (roles role))
     (.content content))))

(defn response-text-config
  "Convert a malli schema to a response text config containing a format for structured outputs. If given a single var,
   the format name will be inferred from the var name. Otherwise a schema and name can be explicitly
   provided."
  ([v]
   (let [s @(resolve v)
         n (name v)]
     (response-text-config s n)))
  ([s format-name]
   (let [schema (mj/transform s)
         config-schema (-> (ResponseFormatTextJsonSchemaConfig$Schema/builder)
                           (.putAdditionalProperty "type" (JsonValue/from (:type schema)))
                           (.putAdditionalProperty "properties" (JsonValue/from (walk/stringify-keys (:properties schema))))
                           (.putAdditionalProperty "required" (JsonValue/from (mapv (comp JsonValue/from name) (:required schema))))
                           (.putAdditionalProperty "additionalProperties" (JsonValue/from false))
                           (.build))
         format (-> (ResponseFormatTextJsonSchemaConfig/builder)
                    (.name format-name)
                    (.type (JsonValue/from "json_schema"))
                    (.schema config-schema)
                    (.strict true)
                    (.build))]
     (-> (ResponseTextConfig/builder)
         (.format format)
         (.build)))))

(defn output-text
  "Extract output text from the response object"
  [^Response response]
  (->> response
       (.output)
       (.stream)
       (.iterator)
       (iterator-seq)
       (first)
       (.asMessage)
       (.content)
       (first)
       (.outputText)
       (.get)
       (.text)))

(defn format-type
  "Get the format type of the response"
  [^Response response]
  (try
    (str (._type (.get (.text (.get (.format (.get (.text response))))))))
    (catch Exception _
      "text")))

(defn create-response
  ":format should be a Var pointing to a malli schema"
  ^Response [entries & {:keys [format]}]
  (let [client        @*client
        input-items   (reduce (fn [items entry]
                                (.add items (easy-input-message entry))
                                items) (ArrayList.) entries)
        create-params (-> (ResponseCreateParams/builder)
                          (.inputOfResponse input-items)
                          (.model ChatModel/GPT_4O)
                          (cond->
                           (some? format) (.text (response-text-config format)))
                          (.build))]
    (-> client
        (.responses)
        (.create create-params))))

(def sizes
  {"1024x1024" ImageGenerateParams$Size/_1024X1024
   "1792x1024" ImageGenerateParams$Size/_1792X1024
   "1024x1792" ImageGenerateParams$Size/_1024X1792})

(defn dall-e-3
  "Generate an image using OpenAI's DALL-E 3 model. Returns a string url of the
   generated image"
  [prompt & {:keys [size] :or {size "1024x1024"}}]
  (let [client        @*client
        create-params (.build (doto (ImageGenerateParams/builder)
                                (.prompt prompt)
                                (.model ImageModel/DALL_E_3)
                                (.size (sizes size))))
        response      (.generate (.images client) create-params)]
    (-> response
        (.data)
        (first)
        (.url)
        (.get))))

(def voices
  {:alloy   SpeechCreateParams$Voice/ALLOY
   :ash     SpeechCreateParams$Voice/ASH
   :ballad  SpeechCreateParams$Voice/BALLAD
   :coral   SpeechCreateParams$Voice/CORAL
   :echo    SpeechCreateParams$Voice/ECHO
   :fable   SpeechCreateParams$Voice/FABLE
   :onyx    SpeechCreateParams$Voice/ONYX
   :nova    SpeechCreateParams$Voice/NOVA
   :sage    SpeechCreateParams$Voice/SAGE
   :shimmer SpeechCreateParams$Voice/SHIMMER
   :verse   SpeechCreateParams$Voice/VERSE})

(def response-formats
  {:mp3  SpeechCreateParams$ResponseFormat/MP3
   :opus SpeechCreateParams$ResponseFormat/OPUS
   :wav  SpeechCreateParams$ResponseFormat/WAV
   :pcm  SpeechCreateParams$ResponseFormat/PCM
   :flac SpeechCreateParams$ResponseFormat/FLAC})

(defn tts
  "Return an input stream containing the results of converting the given text
   to speech, wrapped in a BufferedInputStream for compatibility with playback."
  [text & {:keys [voice instructions response-format]
           :or   {voice :onyx response-format :wav}}]
  (let [client  @*client
        voice   (voices voice)
        fmt     (response-formats response-format)
        builder (doto (SpeechCreateParams/builder)
                  (.input text)
                  (.voice voice)
                  (.responseFormat fmt)
                  (.model SpeechModel/GPT_4O_MINI_TTS))
        _       (when instructions
                  (.instructions builder instructions))
        service (.speech (.audio client))
        response (.create service (.build builder))]
    (.body response)))

(def audio-models
  "The audio models available for transcription"
  {:whisper-1              AudioModel/WHISPER_1
   :gpt-4o-transcribe      AudioModel/GPT_4O_TRANSCRIBE
   :gpt-4o-mini-transcribe AudioModel/GPT_4O_MINI_TRANSCRIBE})

(defn transcribe
  "OpenAI requires a name to be given. It can be any string"
  [file name & {:keys [model prompt]
                :or   {model :whisper-1}}]
  (let [params   (-> (TranscriptionCreateParams/builder)
                     (.file (-> (MultipartField/builder)
                                (.filename name)
                                (.value file)
                                (.build)))
                     (.model (audio-models model))
                     (.language "en") 
                     (cond-> prompt (.prompt prompt))
                     (.build))
        service  (.transcriptions (.audio @*client))
        response (.asTranscription (.create service params))]
    {:text (.text response)}))
