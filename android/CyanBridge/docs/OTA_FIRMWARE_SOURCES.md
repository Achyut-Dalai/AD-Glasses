# OTA Firmware Sources

The Glasses dashboard's OTA section presents a Material 3 source picker after the
operator chooses a chip target:

- **Personal firmware file** opens Android's document picker. The selected file is
  copied into app-private storage before any BLE or Wi-Fi OTA command is sent.
- **Stealth server copy** requests `channel=stealth` from the CyanBridge relay.
- **Debug server copy** requests `channel=debug`. The relay rejects it unless the
  authenticated relay account has the explicit `debugFirmwareAccess` entitlement.

The picker requires acknowledgement that the image matches the connected glasses
model and selected chip. It does not treat a filename extension as proof of image
compatibility.

For either server source, CyanBridge reads the selected chip's current hardware and
firmware versions over BLE before it requests a file. The relay returns an artifact
only when its catalog declares that exact base firmware version, SHA-256 digest, and
byte size. The app checks all three before it starts OTA. A hardware-family match by
itself is rejected.

## Targets And Order

- V821 Wi-Fi/Linux firmware uses `.swu` via BLE OTA mode, Wi-Fi Direct, and the
  phone's HTTP server.
- JieLi BLE firmware uses `.bin` through the vendor `DfuHandle` state machine.

The decompiled official HeyCyan `OTAActivity` starts Wi-Fi OTA first. On a successful
Wi-Fi completion notification (`loadData[6] == 0x07` and `loadData[7] == 1`), its
*app* explicitly requests and starts the BLE `.bin` update. The glasses do not
autonomously begin BLE DFU because a `.swu` succeeded. The official activity has no
manual picker or chip-target selector in that normal path.

CyanBridge intentionally does not auto-chain arbitrary files. A successful `.swu`
therefore ends after the Wi-Fi OTA flow and cannot fail because CyanBridge did not
upload a `.bin`. A single target can still leave a mismatched Wi-Fi/BLE pair if the
image was not built from the installed target-chip version. Server artifacts avoid
that generic-version risk by requiring an exact base match; personal files remain
recovery/lab-only unless their supplier documents compatibility.

When the relay has no exact-base artifact, CyanBridge offers a Material 3 patch
request dialog instead of substituting another version. The user may cancel or enter
a contact email. Sending the request uses the authenticated `/logs/submit` relay
endpoint and includes the requested target, both chip version pairs, the relay
response, and OTA/BLE/P2P logcat diagnostics. The dialog explains that compatibility
between unknown Wi-Fi and BLE versions cannot be assumed and that review normally
requires up to roughly 48 hours.

## Lifecycle Safeguards

- OTA owns the exclusive glasses session, preventing concurrent media sync, preview,
  ADB debug, and background BLE command flows.
- The file type is bound to the selected transport: `.swu` cannot enter BLE DFU and
  `.bin` cannot enter Wi-Fi OTA.
- Server artifacts must match the reported base version, expected byte size, and
  SHA-256 before the OTA manager receives the staged file.
- The OTA manager holds a bounded partial wake lock for the active session, matching
  the official app's wake protection, and releases it after teardown.
- Hardware model, cryptographic signature, and cross-chip compatibility cannot be
  proved from arbitrary local files. Use only recoverable lab hardware for unvetted
  firmware.
