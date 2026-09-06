# HeyCyan integration research

This directory is the living source of truth for the HeyCyan hardware integration used by AD Glasses.

The goal is to keep product code separate from reverse-engineering assumptions. Before implementing a new HeyCyan hardware feature, check the evidence recorded here and update these documents when new evidence changes the model.

## Documents

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — current BLE / Wi-Fi / HTTP / RTSP architecture, native iOS layering, supported capability assessment, and implementation rules.
- [`PROTOCOL.md`](./PROTOCOL.md) — verified GATT UUIDs, outer frame format, CRC rules, confirmed command families/payloads, and reconstructed example frames.
- [`RESEARCH_LOG.md`](./RESEARCH_LOG.md) — chronological findings, corrections, unresolved questions, and artifacts still to inspect.
- [`OFFICIAL_APP_FINDINGS.md`](./OFFICIAL_APP_FINDINGS.md) — static-analysis findings from the user-supplied official HeyCyan production Android package. This file is additive and does not overwrite the earlier research record.
- [`HARDWARE_CAPTURE_AUDIT_2026-08-30.md`](./HARDWARE_CAPTURE_AUDIT_2026-08-30.md) — correlated physical-glasses HCI/logcat audit, including buttons/gestures, “Hey Cyan”, Opus audio, BLE and Wi-Fi boundaries.
- [`MEDIA_PROCESSING_AUDIT_2026-08-30.md`](./MEDIA_PROCESSING_AUDIT_2026-08-30.md) — image/audio/video derivative policy and current native processing coverage.
- [`IOS_BACKEND_READINESS_2026-08-30.md`](./IOS_BACKEND_READINESS_2026-08-30.md) — native BLE/Wi-Fi/Assistant readiness, held controls, and the ordered physical-iPhone validation run.
- [`IOS_HARDWARE_VALIDATION.md`](./IOS_HARDWARE_VALIDATION.md) — conservative one-action-at-a-time validation runbook for the native iPhone implementation.

## Evidence levels

Every important protocol claim should be treated as one of these:

- **PROVEN** — observed in official app/SDK behavior, vendor interface, hardware capture, or multiple independent sources.
- **STRONG** — directly supported by reverse-engineered source or vendor demo code, but not yet verified on our physical glasses.
- **INFERRED** — reasonable architectural conclusion from the available evidence; do not encode as protocol truth without verification.
- **UNKNOWN** — not established. Do not guess packet bytes, endpoints, state transitions, or hardware capabilities.

## Current top-level conclusion

The HeyCyan architecture is not simply “BLE or Wi-Fi.” It is a multi-stage system:

```text
                  HeyCyan glasses
                        │
                 BLE control plane
                        │
        ┌───────────────┼────────────────┐
        │               │                │
   device/status    capture/control   enable IP mode
        │               │                │
        │               │                ▼
        │               │           Wi-Fi subsystem
        │               │                │
        │               │       ┌────────┴────────┐
        │               │       │                 │
        │               │    AP/hotspot        P2P/WFD
        │               │       │                 │
        └───────────────┴───────┴─────────────────┘
                                │
                         high-bandwidth IP
                                │
                       ┌────────┴────────┐
                       │                 │
                 HTTP media/files   RTSP live preview
```

For native iOS, the currently documented viable paths are:

```text
MEDIA
CoreBluetooth / verified command
          ↓
prepare transfer/AP mode
          ↓
NEHotspotConfiguration
          ↓
iPhone joins glasses-hosted AP
          ↓
URLSession / local HTTP media transfer

LIVE PREVIEW
CoreBluetooth / verified AP-live payload
          ↓
NEHotspotConfiguration
          ↓
iPhone joins glasses-hosted AP
          ↓
RTSP :8554/ch0
```

Android also contains a HeyCyan Wi-Fi Direct/P2P path. Do not assume that every operation requires P2P, and do not assume an Android `WifiP2pManager` flow can be translated 1:1 to iOS. The official production app proves AP variants exist for both media transfer and live preview.

## Non-negotiable implementation rule

**Do not invent HeyCyan BLE commands.**

A Swift API such as `takePhoto()`, `getBattery()`, `enableTransferMode()`, or `startLivePreview()` may exist at the product/provider layer before its transport is complete, but its underlying packet, UUID, handshake, response parser, timeout, and state transition must come from verified evidence.

The UI can be designed ahead of transport work. The protocol implementation cannot be guessed.

## Sources currently in scope

Repository evidence already inspected includes:

- `WIFI_TRANSFER_ARCHITECTURE.md`
- `heycyan-core/core-connectivity/`
- `ios/QCSDK.framework/Headers/`
- `ios/QCSDKDemo/`
- `examples/ios/GlassesFramework/`
- `android/CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/media/`
- author reverse-engineering notes and discussions
- current AD Glasses native iOS HeyCyan provider

External artifacts now in scope:

1. Official HeyCyan Android XAPK `1.0.142_20260807` — statically inspected and correlated with a physical-glasses HCI/logcat capture; see `OFFICIAL_APP_FINDINGS.md`, `HARDWARE_CAPTURE_AUDIT_2026-08-30.md`, and `PROTOCOL.md`.
2. Official iOS app binary if available — particularly useful for iOS-specific orchestration; a decrypted IPA gives much deeper executable visibility.
3. CyanBridge APK — secondary because its source is already available and its public release can be obtained independently.
4. Physical-device BLE / Wi-Fi captures when static analysis leaves ambiguity.

## Updating this directory

When an APK, SDK, packet capture, or hardware test changes our understanding:

1. Add the evidence and date to `RESEARCH_LOG.md`.
2. Put production Android artifact findings in `OFFICIAL_APP_FINDINGS.md` without deleting earlier evidence.
3. Put stable byte-level facts in `PROTOCOL.md` only after they are verified.
4. Update `ARCHITECTURE.md` only after the finding is strong enough to affect implementation.
5. Mark conflicting findings explicitly instead of silently replacing them.
6. Keep model/firmware-specific behavior separate when necessary.
