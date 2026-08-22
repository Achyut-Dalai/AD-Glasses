# AD Glasses hardware/runtime backlog

This document intentionally records hardware and security work that should happen **after** the current product/UI/runtime architecture is stable. Do not casually refactor the known-working HeyCyan BLE, Wi-Fi Direct/P2P, media, audio, or OTA paths while completing front-end/product work.

## Wear detection

HeyCyan SDK/protocol evidence indicates wearing-detection commands exist. Before using them in AD Glasses:

- confirm the Android SDK/AAR exposes a stable wearing-detection API or identify the exact command/notification path;
- verify behavior on physical HeyCyan hardware (on-head, off-head, transitions, reconnect, charging/case states);
- decide whether wear state is reliable enough to become a readiness signal rather than merely a hint;
- keep voice wake, recording, camera, and privacy behavior conservative when wear state is unknown;
- avoid implementing from the iOS header alone without Android/hardware verification.

Potential product use after verification: on-head state may allow AD Glasses to become conversationally ready while keeping the phone UI invisible. Off-head state may suppress unsolicited audio or sensitive actions.

## Glasses <-> phone transport security review

Audit without changing behavior first:

### BLE

- Determine whether HeyCyan uses LE Secure Connections, legacy pairing, bonding, or an application-layer session.
- Verify which GATT characteristics require encryption/authentication.
- Check whether a second phone can connect, issue commands, or interfere while the intended phone is connected.
- Check replay/spoofing risk for control commands and notification payloads.
- Review reconnect/auto-pair behavior and stored device identity.
- Confirm sensitive commands cannot be accepted from an untrusted nearby device.

### Wi-Fi Direct / P2P / media HTTP

- Document how P2P credentials are negotiated and who can join the group.
- Confirm whether the glasses HTTP media endpoint is reachable only on the private P2P network.
- Determine whether HTTP requests have any application-layer authentication/session binding.
- Check whether another peer on the group could enumerate/download/delete media or interfere with transfer.
- Review process network binding and route isolation.
- Preserve the known working sequence documented in `android/AGENTS.md` unless hardware testing proves a change is necessary.

## Phone <-> relay/cloud security review

- Require HTTPS for non-local relay endpoints; treat cleartext remote relay configuration as invalid.
- Review bearer/API token storage and ensure secrets are not logged, exported, or placed in conversation history.
- Verify relay capability discovery and chat requests authenticate consistently.
- Review server-side authorization, rate limits, and per-user/session isolation.
- Decide what images/audio/transcripts may leave the phone and expose those choices in privacy settings.
- Review retention/logging policy for relay requests and provider responses.
- Consider certificate pinning only after threat model and operational tradeoffs are understood; do not add it casually.

## Hardware validation checklist

Physical HeyCyan glasses are required before considering this review complete. Validate at minimum:

- pairing and first connection;
- reconnect after app/phone/glasses restart;
- simultaneous/competing phone connection behavior;
- Bluetooth audio input/output and wake interactions;
- camera capture and image-question flow;
- P2P start, glasses IP discovery, media listing and transfer;
- interruption/recovery during P2P transfer;
- battery/storage reporting;
- wear detection if Android integration is confirmed;
- OTA/firmware preflight without performing unnecessary firmware writes.

## Upstream review rule

When AD Glasses upstream changes hardware code, classify each change before porting:

1. HeyCyan protocol/reliability/security discovery -> review promptly and adapt when useful.
2. Generic transport/security improvement -> review for applicability without disturbing stable paths.
3. Meta integration -> keep as a future dedicated product path.
4. Unsupported-device expansion -> learn from reusable discoveries, but do not add product support merely for parity.
5. Play Store, billing, subscription, or public-distribution requirements -> normally out of scope for AD Glasses.
