# Retired Termux Server MVP Design

This document is retained only to record a superseded experiment. The old general-purpose phone/CLI relay design is **not** part of the current AD Glasses runtime and must not be used as an implementation guide.

## Current AI architecture

AD Glasses supports three inference lanes:

1. **Cloud REST** — authenticated requests go directly through the configured API provider and model.
2. **Cloud Realtime / Gemini Live** — bounded AD-owned realtime sessions use the Gemini Live API and the dedicated session-authorization plumbing required for short-lived credentials.
3. **Local fallback** — an installed on-device model may be used when the user selects Local AI or when the configured fallback policy allows it.

Standard text responses remain AD-owned and are spoken with Android TTS. Android Assistant-role integration is also AD-owned; it is not a handoff to another assistant application.

## What was retired

The earlier Termux prototype exposed a broad set of chat, voice, image, capability, entitlement, and transcription endpoints. Those endpoints and their former client/router classes are no longer the canonical application contract. Do not restore them to make old documentation or tests compile.

If a future self-hosted service is added, it should be introduced as a new explicit provider with a documented API and privacy boundary rather than reviving the retired relay architecture.
