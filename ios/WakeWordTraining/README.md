# AD Glasses phone wake-word training

This directory contains production LiveKit WakeWord configurations for AD Glasses.

- `hey_a_d.yaml` is the current production wake phrase, displayed as **Hey A D** and spoken naturally as “hey A-dee”.
- `jarvis.yaml` is the next wake-word candidate for the assistant named **Jarvis**.

The two configurations intentionally use separate LiveKit model/output names so the current Hey A D run can finish and remain usable while Jarvis is trained and evaluated.

The training and iOS dependencies are intentionally pinned to LiveKit WakeWord commit
`95448a7559c453fcd87645bd67b247ffb45f85b0`. The current tagged releases predate the root Swift
package manifest required by Xcode, so the iOS project uses this reviewed commit instead of
following `main`.

## Finish the current Hey A D run

If training has already completed its training phases, finish export and evaluation without changing `hey_a_d.yaml`:

```sh
.venv/bin/livekit-wakeword export hey_a_d.yaml
.venv/bin/livekit-wakeword eval hey_a_d.yaml
```

Then bundle only if the evaluation passes the production gates:

```sh
python3.11 bundle_evaluated_model.py
```

The bundler defaults remain Hey A D, so this command keeps the current app wake phrase/model unchanged while recording LiveKit's evaluated optimal threshold in `Resources/WakeWords/manifest.json`.

## Hey A D pronunciation

LiveKit's Piper backend lowercases phrases and uses `espeak-ng`. With espeak-ng 1.52, the three
positive spellings in `hey_a_d.yaml` resolve to continuous `/heɪ eɪ diː/`. In contrast, `hey a d`
produces “hey uh dee” and `hey ay dee` produces “hey eye dee”, so neither is a positive.

Recheck the phonemes after changing the TTS toolchain:

```sh
espeak-ng --ipa -q -v en-us "hey a-d"
espeak-ng --ipa -q -v en-us "hey a.d."
espeak-ng --ipa -q -v en-us "hey a-dee"
```

Also listen to generated clips in `output/hey_a_d/positive_train` before committing a classifier.

## Jarvis pronunciation

**Jarvis** is pronounced naturally as two syllables, “JAR-vis”. The training target remains the normal spelling `jarvis`; the hyphenated form is only a pronunciation note.

Before committing to the full production run, verify the current Piper/espeak-ng toolchain and listen to generated positives:

```sh
espeak-ng --ipa -q -v en-us "jarvis"
```

If the generated speech sounds wrong, fix the target spelling/pronunciation strategy before spending time on the full training run.

## Production training

Production training needs Python 3.11+, `espeak-ng`, FFmpeg, PortAudio, substantial runtime, and
roughly 30–40 GB of free disk for the 16 GB ACAV100M negative set plus generated and augmented
audio. Shared datasets/backgrounds/RIRs can stay in `data`; the phrase-specific generated outputs live under their own model directories.

Initial environment setup:

```sh
brew install python@3.11 uv espeak-ng ffmpeg portaudio
uv venv --python 3.11 .venv
uv pip install --python .venv/bin/python \
  "livekit-wakeword[train,eval,export] @ git+https://github.com/livekit/livekit-wakeword@95448a7559c453fcd87645bd67b247ffb45f85b0"
```

For the current Hey A D model:

```sh
.venv/bin/livekit-wakeword setup --config hey_a_d.yaml
.venv/bin/livekit-wakeword run hey_a_d.yaml
.venv/bin/livekit-wakeword export hey_a_d.yaml
.venv/bin/livekit-wakeword eval hey_a_d.yaml
python3.11 bundle_evaluated_model.py
```

For Jarvis, keep the Hey A D files intact and run the separate config:

```sh
.venv/bin/livekit-wakeword run jarvis.yaml
.venv/bin/livekit-wakeword export jarvis.yaml
.venv/bin/livekit-wakeword eval jarvis.yaml
```

Do **not** run the Jarvis bundle command until you actually want the app's production wake phrase to switch. Once its evaluation passes and you are ready for the cutover:

```sh
python3.11 bundle_evaluated_model.py \
  --model-name jarvis \
  --phrase "Jarvis" \
  --spoken-as "JAR-vis"
```

That command copies `output/jarvis/jarvis.onnx` into app resources and rewrites `manifest.json` to point at Jarvis with the evaluated threshold.

`bundle_evaluated_model.py` refuses to ship a model unless LiveKit evaluation reports at least
90% held-out recall, no more than 0.10 false positives/hour, and the full 5,000-positive validation
set. The app never guesses a production threshold.

Do not commit `data`, `output`, or a quick/toy classifier. After bundling, inspect and commit the generated `.onnx` file and updated `Resources/WakeWords/manifest.json` together.
