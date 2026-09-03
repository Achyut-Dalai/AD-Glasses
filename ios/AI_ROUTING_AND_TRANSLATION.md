# AD Glasses AI routing and English Live Translation

This document records the iOS 27 AI architecture used by AD Glasses. The design is intentionally hybrid: use the smallest/local component where it adds reliability or privacy, and keep the configured cloud model as the normal answer model.

## Product rule

**Qwen3 0.6B is not the chat model.** Short and long Assistant answers continue to use the configured Cloud AI profile (Groq, OpenAI, Gemini, DeepSeek, OpenRouter, or a custom OpenAI-compatible provider).

The local model is a bounded semantic helper for places where a tiny, always-nearby model is more useful than a large answer model:

- repair/classify short command-like transcripts from the glasses microphone;
- recover a known intent when SpeechAnalyzer heard a nearby word such as `cling` instead of `click`;
- choose only among already-verified high-level product actions;
- provide a future seam for other small structured routing/extraction tasks once physical-device latency is measured.

It does **not** invent BLE commands, does not synthesize stop-recording commands, does not answer ordinary questions, and is not allowed to turn arbitrary conversational text into a hardware action.

## Assistant routing boundary

The existing deterministic `AssistantRequestRouter` remains the authority for clear commands and product routes. Qwen is used before that boundary only for finalized **external glasses PCM** transcripts that look command-like.

```text
glasses Opus / PCM
        |
        v
Apple SpeechAnalyzer
        |
        v
final transcript
        |
        +-- ordinary/question/long text --------------------> unchanged
        |
        +-- short command-like candidate
                |
                v
          Qwen3 0.6B classifier
                |
                v
       deterministic safety policy
          /                 \
       reject              accept
         |                   |
 original transcript   canonical known phrase
         \                   /
          +--------+---------+
                   |
                   v
       AssistantRequestRouter / AppModel
```

The Qwen classifier is allowed to return only:

- `CLICK_PHOTO`
- `START_VIDEO`
- `START_AUDIO`
- `READ_TEXT`
- `NONE`

A model label alone is never enough. AD also requires a high confidence value, no negation/question language, a short transcript, and lexical proximity to an existing command family. The canonical result is then sent through the same existing Assistant routing code as a correctly recognized phrase.

This preserves the repo rule: **never invent proprietary BLE commands or firmware actions.**

### Known acoustic fallback

A tiny deterministic fallback recognizes only a few explicit one-word `click` aliases (`cling`, `clik`, `clic`, `clique`). This keeps the motivating failure mode usable in Simulator or before the Core AI asset is staged. It is deliberately not a generic fuzzy command executor.

## Qwen3 0.6B Core AI model

Chosen model:

- Hugging Face: `Qwen/Qwen3-0.6B`
- Apple runtime: Core AI + `CoreAILanguageModels` + Foundation Models `LanguageModelSession`
- iOS export context: 2048 tokens
- AD folder name: `ADQwen3_0_6B_iOS`

The 2048-token cap is intentional. This model classifies tiny routing prompts; it does not need a large conversational context window, and the smaller static context leaves more memory headroom on the iPhone 13.

### Export

Install `uv` if necessary:

```bash
brew install uv
```

Then from the repo root:

```bash
bash ios/scripts/export-qwen3-coreai.sh
```

The helper pins Apple's exporter source to commit:

```text
cefd53d70a453518861d1958cdbb4dddab8ece34
```

and produces:

```text
ios/.coreai/exports/ADQwen3_0_6B_iOS
```

Generated Core AI assets are ignored by Git and must not be committed.

### CoreAILM package note during the iOS 27 beta

The app source conditionally imports `CoreAILanguageModels` on a physical device. Apple's public `apple/coreai-models` package currently has an open Simulator issue (`apple/coreai-models#49`): linking `CoreAILM` to an app target also makes Xcode try to compile package sources that import the device-only `CoreAI` framework for the iOS Simulator.

For that reason the normal AD target/CI does **not** automatically link `CoreAILM` yet. This preserves the working Simulator and hosted XCTest target while Apple resolves the package issue.

For physical Qwen testing on the iPhone 13:

1. Open `ios/ADGlasses.xcodeproj` in Xcode 27.
2. File > Add Package Dependencies.
3. Add `https://github.com/apple/coreai-models`.
4. Use the revision compatible with the exported model (the export helper currently pins `cefd53d70a453518861d1958cdbb4dddab8ece34`).
5. Add product **CoreAILM** to the ADGlasses target for the physical-device experiment.
6. Select the physical iPhone 13 destination, not Simulator, and build/run.

Until Apple fixes the upstream Simulator package issue, removing that product dependency restores normal Simulator builds. AD's source itself remains safe because it uses `canImport(CoreAILanguageModels)` and excludes the runtime path for Simulator.

This is an upstream beta packaging constraint, not an AD requirement for cloud Assistant or translation.

### Stage the model on the iPhone

Install and launch AD Glasses on the iPhone first. Find the device identifier:

```bash
xcrun devicectl list devices
```

Then:

```bash
bash ios/scripts/stage-qwen3-coreai-device.sh <DEVICE_ID>
```

The runtime expects:

```text
Documents/CoreAIModels/ADQwen3_0_6B_iOS
```

Force-quit and relaunch the app after staging. Qwen prewarm is best effort; failure to find/load the asset never disables SpeechAnalyzer or cloud Assistant.

## Live Translation product requirement

AD Live Translation is now **spoken language -> English**. We do not add a second target-language selector merely because an API can support it.

Two engines are available in the Translate screen.

### Groq Whisper — preferred connected mode

A Groq Cloud AI profile/API key is reused for speech requests. The chat model selected in that profile does not control the Whisper model; the Translate screen has its own speech-model choice.

#### Whisper Large V3

```text
microphone / HFP audio
        |
        v
short M4A utterance
        |
        v
Groq /audio/translations
whisper-large-v3
        |
        v
English text
        |
        +--> screen
        +--> Apple TTS -> glasses / current audio route
```

This is the direct multilingual-audio-to-English path and the accuracy-oriented choice for difficult/noisy audio.

#### Whisper Large V3 Turbo

Groq's public beta documentation has not always agreed about whether Turbo supports the translation endpoint. AD therefore does not rely on ambiguous direct-translation behavior.

```text
microphone / HFP audio
        |
        v
Groq /audio/transcriptions
whisper-large-v3-turbo
        |
        v
source-language transcript
        |
        +-- already English --> use it
        |
        +-- non-English
              |
              v
        Apple Translation -> English
              |
              +-- failure/unsupported --> same audio -> Large V3 /audio/translations
```

That lets the user choose Turbo for fast recognition without making the app brittle if the provider changes/limits Turbo translation semantics.

### Audio-turn behavior

Groq Live Translation deliberately does not leave the microphone recording while TTS plays. Each cycle is:

1. listen and meter audio;
2. detect about one second of post-speech silence (with a maximum-turn safety cap);
3. stop recording;
4. send only that short audio turn to Groq;
5. receive English;
6. speak English;
7. reopen the microphone.

This avoids immediately feeding AD's own English TTS back into Whisper.

### Apple Offline — local fallback

The original iOS translation path remains available:

```text
microphone / HFP
      |
      v
SpeechAnalyzer (selected source language)
      |
      v
Apple Translation .lowLatency
      |
      v
English
      |
      +--> screen
      +--> TTS
```

This path is useful when there is no network, when cloud audio should not be sent, when Groq is rate-limited/unavailable, or while validating cloud behavior. Language assets may need one Apple-managed download before offline use.

## Cloud Assistant remains unchanged

Normal Ask behavior remains:

```text
user / glasses voice
       |
       v
SpeechAnalyzer
       |
       v
deterministic routing + grounding/tool selection
       |
       v
configured Cloud AI model
       |
       v
streamed answer + TTS
```

Qwen does not replace the configured cloud model for simple questions. This avoids adding a local-model hop when Groq/cloud latency and quota are acceptable.

## Physical validation checklist

After the normal app build succeeds, validate on the iPhone 13 in this order:

1. Assistant exact `click` still takes one photo.
2. A glasses-mic `cling`/near-`click` transcript can recover to the existing photo action without generating chat/TTS.
3. Questions containing command words (for example, `How do I take a photo?`) remain conversation and never execute hardware.
4. Negated requests remain conversation and never execute hardware.
5. Groq Large V3: Hindi/Spanish/another supported language -> English text/TTS.
6. Groq Turbo: source transcription -> English, including Large V3 fallback if Apple text translation cannot handle the utterance.
7. Switch to Apple Offline and verify the same chosen source language -> English without Groq.
8. Verify TTS is not re-recorded as a second translation turn.
9. Repeated 10-20 translation turns: watch latency, audio-route stability, heat, and battery.
10. Qwen benchmark on the actual iPhone 13: model load, warm classifier latency, memory pressure, heat, and false-positive action rate.

The most important Qwen metric is not tokens/second. It is **zero unsafe false-positive hardware actions while recovering real speech-recognition mistakes.**
