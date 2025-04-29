# Audio Agents

Audio Agents is a Clojure-based project that demonstrates how to create an audible conversation system using OpenAI's GPT models. This project is designed for Clojure enthusiasts who want to explore the integration of audio input/output with AI-driven conversational agents.

## Features

- **Audio Input**: Capture audio from a microphone and transcribe it into text using OpenAI's transcription API.
- **Audio Output**: Convert text responses into speech using OpenAI's text-to-speech (TTS) capabilities.
- **Conversational Agents**: Create agents with customizable personas and system prompts.
- **Dialogue System**: Facilitate back-and-forth conversations between agents and users.

## Project Structure

```
├── deps.edn                ; Project dependencies
├── dev/                    ; Development-specific files
│   ├── dev.clj
│   └── user.clj
├── resources/              ; Resources for prompts and audio
│   ├── output.wav
│   └── prompts/            ; These prompts are mostly illustrative, written by gpt
│       ├── persona-base.md 
│       └── personas/
│           └── flurbos-fan.md
│           └── grumbos-fan.md
│           └── mark-twain.md
├── src/                    ; Source code
│   ├── audio/              ; Audio input/output utilities
│   │   ├── microphone.clj
│   │   └── playback.clj
│   ├── ayyygents/          ; Workflow utilities for agents
│   │   └── workflow.clj
│   ├── examples/           ; Example usage
│   │   └── conversation.clj
│   │   └── debate.clj
│   └── openai/             ; OpenAI API integration
│       └── core.clj
```

## Getting Started

### Prerequisites

- [Clojure CLI](https://clojure.org/guides/getting_started) installed on your system.
- An OpenAI API key for transcription, TTS, and GPT functionalities.

### Running examples

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd audio-agents
   ```

2. Add your OpenAI API key to the environment.

These examples use the official OpenAI Java SDK, which requires setting the `OPENAI_API_KEY` environment variable.

```clojure
user => (System/getenv "OPENAI_API_KEY") ;; make sure key is in env
```

For now examples can be run from the REPL. Usage can be found at the bottom of the files in `src/examples/*.clj`.

### Easy REPL access

If you have the clojure cli installed, you can get running pretty quickly. CD into the checked out project and then run:

```bash
clj -A:dev
user=> (dev)
dev=> (conversation) ;;; or (debate)
examples.conversation=> (def ch (chat-with-gpt params))
```


## `src/examples/conversation.clj`

Demonstrates a core.async flow for having an ongoing conversation with a gpt fueled persona. Will request access to your microphone.
See `src/audio/microphone.clj` for options. Attempts to make conversation more natural by checking for periods of silence automatically.
Just speak until you're done!

The conversation can be stopped by closing the core.async channel returned by `chat-with-gpt`

### `mic-chan`
Captures audio input from the microphone and transcribes it into text.

### `conversation-partner`
Creates a conversational agent with a customizable persona and system prompt.

### `with-speech`
Adds text-to-speech capabilities to a conversational agent.

### `chat-with-gpt`
Starts a dialogue with GPT using a specified persona and system prompt.

## `src/examples/debate.clj`

Builds on the conversation example, but it is instead two agents talking to
one another audibly.

### `debate`
Returns a `dialogue` channel that starts conversation between two agents. Audio playback stops when the channel is closed.

## Customization

- **Personas**: Add new persona prompts in the `resources/prompts/personas/` directory.
- **System Prompts**: Modify the base system prompt in `resources/prompts/persona-base.md`.
- **Voice and Instructions**: Customize the voice and playback instructions in the `chat-with-gpt` function.

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests to improve the project.
