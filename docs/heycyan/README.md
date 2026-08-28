# HeyCyan integration research

This directory is the living source of truth for the HeyCyan hardware integration used by AD Glasses.

The goal is to keep product code separate from reverse-engineering assumptions. Before implementing a new HeyCyan hardware feature, check the evidence recorded here and update these documents when new evidence changes the model.

## Documents

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — current BLE / Wi-Fi / HTTP architecture, native iOS layering, supported capability assessment, and implementation rules.
- [`RESEARCH_LOG.md`](./RESEARCH_LOG.md) — chronological findings, corrections, unresolved questions, and artifacts still to inspect.

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
   device/status    capture/control   enable transfer
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
                         HTTP/media transfer
```

For native iOS, the currently documented viable path is:

```text
CoreBluetooth / QCSDK command
          ↓
request transfer mode
          ↓
receive SSID/password and device-IP readiness over BLE
          ↓
NEHotspotConfiguration
          ↓
iPhone joins glasses-hosted AP
          ↓
URLSession / local HTTP media transfer
```

Android also contains a HeyCyan Wi-Fi Direct/P2P path. Do not assume that every operation requires P2P, and do not assume an Android `WifiP2pManager` flow can be translated 1:1 to iOS.

## Non-negotiable implementation rule

**Do not invent HeyCyan BLE commands.**

A Swift API such as `takePhoto()`, `getBattery()`, or `enableTransferMode()` may exist at the product/provider layer before its transport is complete, but its underlying packet, UUID, handshake, response parser, timeout, and state transition must come from verified evidence.

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

External artifacts to compare next:

1. Official HeyCyan Android APK — highest priority.
2. Official iOS app binary if a **decrypted** IPA is available — useful, but not required to proceed.
3. CyanBridge APK — secondary because its source is already available.
4. Physical-device BLE / Wi-Fi captures when static analysis leaves ambiguity.

## Updating this directory

When an APK, SDK, packet capture, or hardware test changes our understanding:

1. Add the evidence and date to `RESEARCH_LOG.md`.
2. Update `ARCHITECTURE.md` only after the finding is strong enough to affect implementation.
3. Mark conflicting findings explicitly instead of silently replacing them.
4. Keep model/firmware-specific behavior separate when necessary.
