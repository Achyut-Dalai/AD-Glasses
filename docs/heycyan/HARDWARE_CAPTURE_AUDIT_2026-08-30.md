# HeyCyan physical-glasses capture audit — 2026-08-30

## Evidence set

This audit correlates the user's real `JS-01 Pro_B0B1` glasses with:

- official HeyCyan Android app `1.0.142_20260807` (`versionCode 142`);
- Android bugreport/logcat and Bluetooth HCI snoop captured while product actions were performed;
- the official app DEX response/command implementations;
- the repository's QCSDK, CyanBridge and `heycyan-core` evidence.

Observed device versions:

```text
Bluetooth firmware  AM01CY_2.20.10_260411
Bluetooth hardware  AM01CY_V2.2
Wi-Fi firmware      WIFIAM01CY_1.00.29_2603141830
Wi-Fi hardware      WIFIAM01CY_V2.2
```

This was a read-only audit. No command was replayed to the glasses.

## Conclusions that are safe to implement

### BLE transport and wire framing

The physical capture validates the production large-data path:

```text
write handle         0x008E, write without response
notification handle  0x0090
frame                 BC CMD LEN_LO LEN_HI CRC_LO CRC_HI PAYLOAD...
CRC                   CRC-16/MODBUS over payload only, little-endian
```

Notifications can contain one application frame or fragments of a larger application frame.
Reassembly must operate above ATT notification boundaries and recover at the next CRC-valid `BC`
marker after corruption.

Observed frame families include:

```text
0x41 control/working-mode requests and replies
0x42 battery
0x43 device information
0x44 AI voice-wake setting
0x51 music/call/system volume control
0x59 glasses microphone Opus packets
0x73 unsolicited device notifications
0xFD thumbnail chunks
```

### “Hey Cyan” and the rear assistant button

The wake-word detector is in the glasses firmware, not a continuously listening Android app:

```text
TX  BC 44 ... 01 00   read AI voice-wake setting
RX  BC 44 ... 01 01   voice wake is enabled
```

The SDK writes the same setting with payload `02 <enabled>`.

When the glasses activate the assistant, the phone receives:

```text
BC 73 ... 03 01       glasses recognition/listening started
BC 59 ... <40 bytes>  encoded microphone packets
BC 73 ... 0A 01       glasses recognition/listening ended
```

Official-app code starts its phone-side speech/Assistant pipeline only after notification subtype
`0x03`, and stops it after subtype `0x0A`. It does not run an Android wake-word recognizer for
“Hey Cyan”. The AM01 guide also assigns a click of the right rear button to assistant activation.

The captured `0x73 03 01` event does **not** carry a source field distinguishing voice wake from
rear-button activation. Both paths converge on the same provider event. AD Glasses should therefore
start one glasses-neutral Assistant input session for either activation source and must not invent a
source label.

### Glasses microphone audio

Family `0x59` begins immediately after assistant-start notifications. The observed payload is a
fixed 40-byte Opus packet container. The official decoder uses its default configuration:

```text
codec        Opus
sample rate  16,000 Hz
channels     1
packet size  40 bytes
PCM output   16-bit mono at 16 kHz
```

The official app queues each complete 40-byte payload into the Jieli Opus decoder, then sends the
decoded PCM to speech recognition. There is no additional application header to remove from the
already-decoded `0x59` frame payload.

The native iOS implementation now decodes the captured packet with `AVAudioConverter` using
Apple's system Opus codec and emits 16-kHz mono PCM through a provider-neutral Assistant-audio
interface. A captured packet decodes successfully on the iPhone 13 simulator (the first packet may
contain fewer than 320 output samples because the system decoder trims codec startup delay).
Bounded pre-recognition buffering and Apple Speech ingestion are implemented; the capability remains
unadvertised until a live physical-iPhone Assistant session is validated.

### Physical music controls and touch gestures

The guide supplied with this physical AM01 unit establishes the product-level control map:

| Control | Gesture | Glasses behavior |
| --- | --- | --- |
| Key 1 | short press | Take a photo and store it on the glasses |
| Key 1 | quick double press | Start video recording |
| Key 1 | short press while recording | Stop video recording |
| Key 2 | short press | Activate the AI voice Assistant; the official guide requires HeyCyan in the foreground |
| Key 2 | quick double press | Activate AI image recognition |
| Key 2 | hold 3 seconds | Start glasses-local audio recording; hold again to stop |
| Touch area | double tap during music | Play/pause |
| Touch area | triple tap during music | Previous track |
| Touch area | long press during music | Next track |
| Touch area | forward/back swipe during music | Raise/lower volume |

Key 1 also owns power-on, power-off, forced-restart and destructive factory-reset hold durations,
and handles incoming-call answer/reject. Those are firmware/headset behaviors—not app controls—and
AD Glasses must never synthesize them or expose a guessed BLE equivalent. In particular, factory
reset remains deliberately outside the normal app path.

The Android system attributes play, pause, previous and next events to `com.android.bluetooth` and
to the paired `JS-01 Pro_B0B1` device. Volume changes are likewise system Bluetooth/audio events.
This proves those physical gestures/buttons use the normal Bluetooth audio/control connection
(A2DP/AVRCP), rather than requiring AD Glasses to translate BLE notifications into synthetic media
keys.

On iPhone, these controls belong to the system Bluetooth pairing. The iOS app must not attempt to
inject media keys into other apps or duplicate a gesture already handled by iOS.

### Glasses volume settings

The official volume page and the physical capture independently confirm BLE family `0x51`. It is a
three-channel glasses setting, separate from the touch strip's normal Bluetooth media gesture:

```text
read payload     01
response fields  music min/max/current, call min/max/current,
                 system min/max/current, current-volume-type
```

The captured unit reported music `0...16`, calls `0...15`, and system `0...16`; these are device
responses, not universal constants. A write must preserve all ranges and unchanged channel values
from the latest read, change only the requested current value, and send the 14-byte SDK structure.
The native UI therefore uses the glasses-reported ranges rather than hardcoding slider limits.

### BLE and Classic Bluetooth are complementary

The official Android session establishes both:

- BLE for app control, device status, voice packets and Wi-Fi orchestration; and
- Classic Bluetooth profiles for audio, calls and normal headset media controls.

CoreBluetooth cannot create or manage an arbitrary Classic Bluetooth bond. AD Glasses can own the
BLE session, while the user pairs/selects the glasses audio route through iOS system Bluetooth.

### Wi-Fi/media transfer

The capture confirms BLE prepares the glasses networking mode and remains connected while Android
uses the glasses' local Wi-Fi and HTTP media server. Production media resources include:

```text
/files/media.config
/files/<filename>
/files/log/<filename>
```

Android uses Wi-Fi Direct for the observed transfer. Native iOS cannot reproduce Android's public
`WifiP2pManager` flow, so its supported route remains the official AP variant plus
`NEHotspotConfiguration`. That AP response/SSID readiness sequence must be validated on the real
iPhone before enabling the product-facing sync button.

Transfer recovery should be transactional:

1. issue the verified BLE prepare request;
2. accept SSID/passphrase only from the matched work-type `04` response and address only from the
   subsequent `0x73/0x08` device notification;
3. join only that validated glasses AP;
4. poll the local media manifest with a deadline;
5. keep BLE alive during HTTP work;
6. on cancellation or Wi-Fi/HTTP failure, leave the temporary association and send the verified
   transfer-finish command when BLE is still ready;
7. if BLE is lost, perform local cleanup only and reconnect normally—never guess a reset command.

### Firmware/OTA

The log proves the official app queried firmware metadata and reported the connected glasses as
current. It does not yet provide a successful OTA payload-transfer trace, bootloader transition or
rollback sequence. OTA, factory reset and restart remain outside the executable integration phase.
The provider declares non-executable product placeholders for them so their eventual UI and
architecture are visible without making any packet sendable. They must not be enabled from
metadata-query or static call-site evidence alone.

## Capture limitations

The session contains many user actions in one continuous timeline. The physical guide supplies the
user-facing button/gesture mapping, while the capture establishes the transport paths. It is still
not sufficient to assign every physical action to a unique BLE notification subtype, because
several embedded actions converge on the same state event. This is not a blocker for app capture
controls: the glasses firmware executes its physical controls, while the app uses verified `0x41`
commands.

## Implementation boundary after this audit

Safe now:

- production frame codec/reassembly;
- bounded BLE connection and reconnect behavior;
- strict response matching within shared family `0x41`;
- battery and device-information parsing;
- music/call/system volume read and write;
- confirmed capture/control commands;
- assistant start/stop event parsing;
- preservation of complete 40-byte `0x59` Opus packets;
- transactional AP/HTTP media-transfer cleanup.
- captured local-clock synchronization and Classic Bluetooth connection request during ready setup.

Still held:

- enabling product microphone capability before live Opus decode + speech ingestion is validated on a physical iPhone;
- assigning assistant activation to voice versus button when the device event does not say;
- AP credentials/readiness values not yet observed from the iPhone-compatible path;
- executable OTA, restart, factory reset, ANC/noise controls and livestream;
- any undocumented command or response field.
