# Native iOS backend readiness — 2026-08-30

This is the implementation audit for the current SwiftUI app, the physical Android HCI capture,
the bundled HeyCyan SDK, `heycyan-core`, and the repository's Wi-Fi architecture research.

## Foundation now implemented

### BLE lifecycle

- CoreBluetooth scans only for the verified HeyCyan primary service.
- A session becomes ready only after both verified services, both write characteristics, both
  notify characteristics, write-without-response support, and notification subscription succeed.
- Writes respect the peripheral's negotiated maximum length and CoreBluetooth back-pressure.
- Incoming application frames are reassembled across arbitrary notification boundaries, CRC
  checked, and resynchronized after malformed data.
- Requests are correlated by family and, for shared family `0x41`, by work type/mode. A random
  notification cannot complete an unrelated operation.
- User disconnect disables reconnect but keeps the remembered glasses. Forget disconnects and
  removes the saved CoreBluetooth identifier. It cannot remove the separate iOS Classic Bluetooth
  pairing; that remains a system Settings action.
- Unexpected disconnect uses bounded 2/5/10/20/30-second retries. Bluetooth power restoration and
  CoreBluetooth state restoration are handled. A timeout/disconnect callback race no longer
  consumes two retry slots.
- Welcome-screen auto-connect starts immediately while the brief launch moment is still displayed.

### Ready-session initialization

The native sequence now includes the captured production operations:

1. family `0x40` clock/language/time-zone synchronization;
2. family `0x42` battery and charging refresh;
3. family `0x43` device/firmware information refresh;
4. family `0x51` music/call/system volume refresh;
5. family `0x49` Classic Bluetooth audio/control connection request.

The exact SDK BCD/time-zone algorithm and the captured India vector are regression tested.

### Controls and status

- Photo, video start/stop, local audio-record start/stop, AI photo, media prepare/finish, P2P
  cleanup, thumbnail chunks, battery, device information and volume structures use only captured
  or supplied-SDK values.
- Photo is product-facing. Video, AI photo, local audio recording and media sync remain beneath a
  hardware-validation boundary until their complete state/result sequences are exercised from the
  iPhone.
- Music/call/system volume uses the ranges returned by this pair of glasses; values are not
  hardcoded. Physical touch music/volume gestures stay in iOS/Classic Bluetooth rather than being
  duplicated as BLE commands.

### Wi-Fi and media

- BLE remains connected and initiates transfer; HTTP is only the bulk-media phase.
- Native iOS requests AP mode, never Android Wi-Fi Direct.
- SSID/passphrase are accepted only from the matched work-type `04` response. Device IP is accepted
  only from the asynchronous `0x73/0x08` notification. The app does not hardcode or ask UI code to
  invent any of them.
- `NEHotspotConfiguration`, local-network permission, the Hotspot Configuration entitlement,
  deadline-based server readiness, non-cellular ephemeral HTTP, redirect rejection, safe file
  names and transactional cleanup are implemented.
- The AP response sequence is not yet physically validated on the iPhone, so Library sync is not
  advertised as complete.

### Glasses Assistant audio

- `0x73/03 01` opens a provider-neutral Assistant input session.
- Complete 40-byte family `0x59` Opus packets are decoded natively at 16-kHz mono and passed as PCM
  to either Apple SpeechAnalyzer or the legacy Apple Speech API.
- Startup audio buffering is bounded to about two seconds. `0x73/0A 01` finalizes one conversation
  turn, preserves it locally, invokes the configured AI provider, and speaks the answer through the
  selected highest-quality installed Apple voice.
- Ava, Zoe, Samantha and Alex are preferences within their actual installed quality tier; the app
  does not claim or download a voice that is absent. The user can choose among all installed
  standard/enhanced/premium voices.
- Physical-iPhone recognition and audio-route testing remain before advertising the microphone
  capability as fully verified.

## Product backends already present

- Conversations are stored atomically with complete file protection, survive launch, support
  open/new/delete/delete-all, and send a bounded recent context without deleting older local turns.
- Assistant routing currently selects only executors that actually exist: conversation, visual
  question, or clarification. It deliberately does not pretend that keywords are working weather,
  sports, places or search tools. Those routes should be registered only with real services.
- Lens performs bounded orientation-correct image preparation, local Vision OCR, voice-or-text
  questions, local Apple Translation, and spoken output. General visual questions correctly remain
  unavailable until a visual-model adapter exists.
- Translation uses Apple's native framework; Google ML Kit is not required. Current translation is
  phrase-based. A true hands-free loop needs explicit listen/segment/translate/speak/resume state so
  it does not retranscribe its own spoken result.

## Media processing decision

- Synced originals remain byte-for-byte originals.
- Lens creates a bounded, oriented JPEG derivative and performs local OCR. It does not silently
  sharpen, denoise, recolor or generatively alter evidence.
- Live Assistant audio currently performs only required Opus decoding/format conversion. Denoise,
  gain or EQ should be added only after quiet/street/wind recordings demonstrate a measurable word
  error-rate improvement.
- Glasses-local `.opus` files still need container validation before playback/transcription; an
  entire file must not be assumed to use the live 40-byte packet framing.
- MP4 remains original; AVFoundation metadata/thumbnails do not require transcoding.

## Intentionally non-executable placeholders

- Firmware update / OTA
- Factory reset
- Forced restart
- Custom wake phrase

The restart work type `0x0E` and OTA work type `0x05` exist in static evidence, but neither is
sendable from the native provider. Factory reset lacks a dedicated captured app command/result.
OTA additionally lacks the real signed artifact-source, bootloader/DFU transition, post-flash
verification and recovery/rollback trace. These operations must remain inert until those paths are
captured deliberately.

## Wake phrase, shutter sound and LED

The current `Hey Cyan` detector is glasses-side. Family `0x44` exposes wake listening off/on; the
capture contains no phrase model or phrase text. Safe alternatives before firmware research are:

- keep the physical Assistant button as the dependable no-wake-word entry;
- expose the verified wake-listening toggle later;
- optionally add an iPhone-foreground `Hey AD` detector as a separate phone-microphone feature,
  with clear battery/audio-route limitations;
- use an App Intent/Siri phrase for phone-owned entry.

Changing the embedded phrase requires firmware evidence and a recovery method, not a renamed app
setting. The SDK's family `0x52` method is named `speakSoundSwitch`, but that name does not prove it
controls the camera shutter. Shutter/LED behavior must be characterized independently and is not
part of the normal backend integration.

The reverse-engineering author additionally reports that wake-word, shutter-sound and LED paths are
encrypted/vendor-controlled. Treat that as a practical firmware boundary unless a signed firmware
artifact and a reversible recovery route are obtained. An app-owned `Hey AD` detector can be built
without touching those paths, but it listens to the iPhone microphone; HeyCyan does not provide a
continuous glasses-microphone stream before its own wake/button event.

## First physical-iPhone validation order

1. Connect/disconnect/forget/relaunch/Bluetooth-off-on and confirm one reconnect attempt per delay.
2. Verify `0x40`, `0x42`, `0x43`, `0x51`, and `0x49` request/response vectors and real UI state.
3. Test glasses button/`Hey Cyan` audio in quiet speech only; save packet diagnostics if decoding or
   transcription fails.
4. Exercise photo once and verify acknowledgement plus physical media count/status.
5. Exercise AP media preparation once without downloading; capture returned mode, credential
   lengths, `0x73/08` address, association result and cleanup.
6. Only after step 5 succeeds, list media and download one small photo to a temporary Library item.
7. Capture OTA check-only screens passively. Do not start an update, restart or reset during the
   foundation session.
