# Jarvis wake-word training for AD Glasses

Jarvis is now the assistant identity and production wake phrase for AD Glasses.

This directory is pinned to LiveKit WakeWord commit
`95448a7559c453fcd87645bd67b247ffb45f85b0`, matching the Swift package revision used by the iOS app.

## Pronunciation

**Jarvis** is pronounced as two syllables: **JAR-vis**. Keep the training target as the normal spelling `jarvis`; the hyphenated form is only a pronunciation note.

Before starting the overnight run, verify your local TTS toolchain:

```sh
espeak-ng --ipa -q -v en-us "jarvis"
espeak-ng -v en-us "jarvis"
```

Listen to a few generated positives before committing to the full run. If Piper/espeak-ng pronounces the word incorrectly, fix that first.

## Environment

Production training needs Python 3.11+, `espeak-ng`, FFmpeg, PortAudio, substantial runtime, and roughly 30–40 GB of free disk for the ACAV100M negatives plus generated and augmented audio.

```sh
brew install python@3.11 uv espeak-ng ffmpeg portaudio
uv venv --python 3.11 .venv
uv pip install --python .venv/bin/python \
  "livekit-wakeword[train,eval,export] @ git+https://github.com/livekit/livekit-wakeword@95448a7559c453fcd87645bd67b247ffb45f85b0"
```

If `data/` already contains the downloaded LiveKit assets, backgrounds, RIRs, and ACAV100M feature set from the previous classifier, keep it. Those are reusable and do not need to be downloaded again.

If this is a fresh checkout or required assets are missing:

```sh
.venv/bin/livekit-wakeword setup --config jarvis.yaml
```

## Production configuration

`jarvis.yaml` intentionally follows LiveKit's production-scale settings:

- 25,000 training samples per phrase class
- 5,000 validation samples per phrase class
- 2,000/500 standalone background samples
- 3 augmentation rounds
- medium conv-attention classifier
- 100,000 phase-1 steps, followed automatically by 10,000 phase-2 and 10,000 phase-3 steps
- target false positives: 0.10/hour
- 5,000-positive held-out evaluation gate before bundling

The TTS batch size is **25**, not LiveKit's upstream production suggestion of 50. This is deliberate: this project previously encountered resource/stability trouble at 50, and the lower value is safer for an unattended overnight run. It changes throughput, not the intended dataset size.

## Recommended overnight run

From `ios/WakeWordTraining`:

```sh
.venv/bin/livekit-wakeword run jarvis.yaml
```

`run` performs generation, augmentation/feature extraction, and the three-phase training pipeline. The final PyTorch classifier is written under `output/jarvis/`.

After training succeeds, export and evaluate explicitly:

```sh
.venv/bin/livekit-wakeword export jarvis.yaml
.venv/bin/livekit-wakeword eval jarvis.yaml
```

Then bundle only the evaluated classifier:

```sh
python3.11 bundle_evaluated_model.py
```

The bundler refuses to ship unless LiveKit reports all of the following:

- at least 90% held-out recall
- no more than 0.10 false positives/hour
- at least 5,000 held-out positive evaluation samples
- a valid calibrated threshold between 0 and 1

On success it copies `output/jarvis/jarvis.onnx` to `ADGlasses/Resources/WakeWords/jarvis.onnx`, removes any legacy `hey_a_d.onnx` still present in that resource directory, and rewrites `manifest.json` with Jarvis plus LiveKit's evaluated optimal threshold.

## Before leaving it overnight

Run these checks first:

```sh
# Confirm the pinned package/CLI is available.
.venv/bin/livekit-wakeword --help

# Confirm Jarvis pronunciation locally.
espeak-ng --ipa -q -v en-us "jarvis"

# Confirm required disk space is available.
df -h .
```

If you want an end-to-end smoke test before the production run, temporarily copy `jarvis.yaml`, reduce `n_samples`, `n_samples_val`, augmentation `rounds`, and `steps`, and run the copy. Do not weaken the production `jarvis.yaml` just to make a test complete faster.

Do not commit `data/`, `output/`, or a toy classifier. Commit the evaluated `jarvis.onnx` and generated `manifest.json` together after the final bundling step.
