# Hey A D classifier

This directory contains the production LiveKit WakeWord configuration for the spoken phrase
“hey A-dee”. The user-facing phrase remains **Hey A D**.

The training and iOS dependencies are intentionally pinned to LiveKit WakeWord commit
`95448a7559c453fcd87645bd67b247ffb45f85b0`. The current tagged releases predate the root Swift
package manifest required by Xcode, so the iOS project uses this reviewed commit instead of
following `main`.

## Pronunciation

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

## Production training

Production training needs Python 3.11+, `espeak-ng`, FFmpeg, PortAudio, substantial runtime, and
roughly 30–40 GB of free disk for the 16 GB ACAV100M negative set plus generated and augmented
audio. From this directory:

```sh
brew install python@3.11 uv espeak-ng ffmpeg portaudio
uv venv --python 3.11 .venv
uv pip install --python .venv/bin/python \
  "livekit-wakeword[train,eval,export] @ git+https://github.com/livekit/livekit-wakeword@95448a7559c453fcd87645bd67b247ffb45f85b0"
.venv/bin/livekit-wakeword setup --config hey_a_d.yaml
.venv/bin/livekit-wakeword run hey_a_d.yaml
python3.11 bundle_evaluated_model.py
```

`bundle_evaluated_model.py` refuses to ship a model unless LiveKit evaluation reports at least
90% held-out recall, no more than 0.10 false positives/hour, and the full 5,000-positive validation
set. It copies the classifier into app resources and records LiveKit's evaluated optimal threshold
in `manifest.json`; the app never guesses a production threshold.

Do not commit `data`, `output`, or a quick/toy classifier. After bundling, inspect and commit both
`Resources/WakeWords/hey_a_d.onnx` and the updated `manifest.json` together.
