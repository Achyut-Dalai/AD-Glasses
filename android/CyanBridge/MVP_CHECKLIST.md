# CyanBridge Accessibility And Multilingual Execution Board

Last updated: 2026-07-16

## Product Outcome

Enable blind and low-vision users to operate CyanBridge independently with TalkBack, reconnect to their glasses reliably, receive scene descriptions in their chosen language, and use a future hands-free Walking Mode safely as situational awareness.

## Completed Foundation

- Added shared Compose resource catalogs for English, Portuguese (Brazil), Spanish, German, French, Italian, Simplified Chinese, and Russian.
- Added Android per-app language selection and an Android 13+ locale configuration. The pairing flow, language setting, vision-profile controls, and new plugin controls now use the shared catalog.
- Added Walking and Detailed vision profiles plus editable cross-provider vision instructions. The image relay client now sends the active prompt on the first image request.
- Preserved local configured and runtime system instructions for LiteRT image/audio requests instead of reducing multimodal input to the final user message.
- Changed vision speech to select the active profile language rather than always selecting US English.
- Made the saved device-profile MAC the first reconnect source, stabilized scan-list publishing, and replaced the decorative connection label with an explicit Connect button.
- Added an accessible Select action and selected state to preview plugin cards. This intentionally does not claim that preview catalog entries are installed.
- Added Native Plugin Framework: `NativePluginCardData` model, native plugin section in CommunityPluginsScreen with toggle switch and settings cog button, `CommunityPluginPrefs` native plugin enable/disable persistence.
- Added Walking Aid native plugin: full foreground service (`WalkingAidService`) with glasses thumbnail capture loop, parallel image description + depth estimation via local/cloud models, state model decision (WARN/DESCRIBE/SKIP) with last 5 descriptions context, TTS output with language-aware locale, auto audio capture pause/resume, safety disclaimer. Settings activity with capture interval (2-30s), model selection (local/cloud for image/depth/state), depth toggle, TTS toggle, history management. Chat activity for Q&A about captured images. All 8 languages localized.

## In Progress

| Work item | Scope | Evidence required |
|---|---|---|
| Walking Aid physical testing | Test the Walking Aid plugin on real glasses with TalkBack enabled. Verify capture loop, TTS output, state model decisions, depth estimation, auto audio pause/resume, and Q&A chat. | Physical device test log, TalkBack walkthrough, battery impact notes. |
| Full shared-screen localization | Move the remaining commonMain literals and Android legacy strings into resource catalogs. | No English-only user-facing strings on supported primary flows. |
| Accessibility regression suite | Add semantic tests and a physical-device script for TalkBack, large text, keyboard navigation, and Android 16. | Test evidence recorded per release. |

## Next

| Work item | Scope | Acceptance criteria |
|---|---|---|
| Real plugin actions | Replace preview-only plugin cards with backend-driven install/select actions, semantic buttons, and installed/enabled state. | A TalkBack user can activate every advertised action and receives state feedback. |
| DepthAnything local integration | Add DepthAnything V2 TFLite model to local model catalog. Create DepthEstimatorEngine for on-device depth maps. | Depth inference works offline without cloud. |
| Phone camera fallback | Add CameraX-based capture as fallback when glasses are not connected (currently toast + refuse to start). | Walking Aid works without glasses connected. |

## Safety Rules

- Vision descriptions are situational awareness only. They must not claim a route or obstacle-free path is safe.
- Walking Mode must use fresh captures only; a stale image fallback is not acceptable for navigation assistance.
- A background vision loop must visibly disclose that it is active and must always offer an immediate Stop action.
- Never add an Install button for a catalog item until it performs a real, accessible install or selection action.
- Walking Aid must not start if glasses are not connected (toast notification instead).
- Walking Aid must pause Auto Audio Capture and refuse to start during active meeting capture.
- The safety disclaimer must be spoken on the first warning of each Walking Aid session.

## Languages

| Code | Language |
|---|---|
| `en` | English |
| `pt-BR` | Portuguese (Brazil) |
| `es` | Spanish |
| `de` | German |
| `fr` | French |
| `it` | Italian |
| `zh-CN` | Simplified Chinese |
| `ru` | Russian |
