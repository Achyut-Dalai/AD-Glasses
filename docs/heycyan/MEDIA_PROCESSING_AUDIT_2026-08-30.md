# AD Glasses media-processing audit — 2026-08-30

## Product rule

Synced originals should remain byte-for-byte device media in the Library. Processing should create
a bounded derivative only for a concrete consumer such as Lens, OCR, cloud vision, transcription,
playback, or a thumbnail. Cosmetic filters must not silently replace originals.

## Images

The Android cloud-vision path performs orientation correction, sampled decode, bounded resize and
JPEG recompression. Its standard and text-detail paths use different resolution/quality budgets.

The native iOS Lens path already provides the equivalent essentials using ImageIO:

- rejects empty and over-30-MB inputs;
- decodes without allocating an unrestricted full-resolution bitmap;
- applies embedded orientation while creating a thumbnail;
- bounds the longest edge to 2,048 pixels;
- creates an 86% JPEG derivative;
- runs accurate, language-corrected Vision OCR locally.

Portrait/landscape is automatic rather than a quality mode: ImageIO reads EXIF orientation and
bakes the transform into the derivative, after which pixel width/height describe the resulting
portrait, landscape or square image. The UI reports that resolved orientation. A manual rotate tool
should be added only if full-resolution glasses captures prove their metadata is missing or wrong.

This is suitable for text/OCR and future visual-model requests. It intentionally applies no
sharpening, denoise, color grading, face enhancement, or generative fill. Those may change text or
scene evidence and should not be defaults. A future cloud-vision adapter may add Android-like
standard/text-detail budgets without changing the saved original.

The Library copies imported stills and videos byte-for-byte. The 2,048-pixel/86% Lens copy is an
analysis derivative, not a replacement or an “enhanced original.” Automatic cosmetic enhancement
is deliberately absent because it can create inconsistent color, sharpening halos and lost detail.

## Live Assistant audio

The verified path is fixed 40-byte Opus → Apple's system Opus decoder → 16-kHz mono PCM → Apple
Speech. The app performs format conversion only when SpeechAnalyzer requests a different native
format. It does not currently apply gain, equalization, echo cancellation or denoise.

That is deliberate. The glasses firmware and Opus encoder may already process the microphones, and
unmeasured denoise can remove consonants or reduce recognition quality. First validate real speech
from quiet, street, wind and music-background cases. Add processing only when recordings demonstrate
a repeatable problem, and compare word-error rate before and after.

## Synced audio recordings

Glasses-local `.opus` recordings are accepted by the Library as originals, but a playback/transcript
derivative pipeline is not complete yet. Before exposing transcription for them, identify the exact
file container (raw Opus versus Ogg Opus), duration metadata and any framing distinct from live
family `0x59`; do not feed an entire file into the live 40-byte decoder by assumption.

## Video

Transferred MP4 remains original media. AVFoundation can generate thumbnails and inspect duration,
orientation and codec without re-encoding. Stabilization, color correction and transcoding should
be explicit export actions only if a real product need appears.
