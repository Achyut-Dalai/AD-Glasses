# Native iOS backend readiness — revised 2026-08-31

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

The 2026-08-31 Android log does **not** show task/backgrounding unpairing the glasses. It shows the
official app explicitly calling `BluetoothDevice.removeBond()` during its Unpair action. Ordinary
backgrounding only changed the app's foreground flag. AD Glasses therefore keeps Disconnect and
Forget separate: Disconnect does not erase the remembered CoreBluetooth peripheral; Forget does.

### Lock screen and background execution

- The app declares only the justified `bluetooth-central` and `audio` background modes.
- CoreBluetooth state restoration can relaunch the provider for accessory events and rebuild its
  verified GATT session. If the user force-quits the app, iOS will not relaunch it for Bluetooth;
  the user must open AD Glasses once again. The same first-open requirement applies after reboot.
- Phone `Hey AD` listening must first start while the app is active. Once its recording session is
  established, it is designed to continue while another app is used or the phone is locked. It
  stops immediately on glasses disconnect/feature disable and pauses for Speech/TTS.
- Wake models, conversations, imported media and Library indexes use after-first-unlock file
  protection. They remain device-only but can be read and written after a subsequently locked
  screen. AI and Porcupine credentials use matching device-only Keychain accessibility.
- A spoken Assistant request receives a finite iOS background-task lease to finish its network
  answer and local persistence; spoken output then uses background audio. This is bounded work,
  not a claim of unrestricted background execution.
- The glasses AP association is explicit and temporary, but no longer uses `joinOnce`: Apple
  removes a `joinOnce` configuration after the app backgrounds for over 15 seconds or the device
  sleeps. AD Glasses records and removes its own temporary SSID on finish, cancellation, failure,
  disconnect, or next launch.
- The complete wake → Speech → answer → wake-resume handoff and Wi-Fi download while locked still
  require the physical-iPhone run. Simulator lifecycle tests cannot prove microphone, Bluetooth,
  local-network, or system-suspension behavior.

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
- Photo and user-initiated Library sync are product-facing. The Android/HCI capture now proves the
  visual-assistance path: detailed AI-photo request, `0x73` ready event, then 50 sequential `0xFD`
  chunks containing a 960×720 JPEG. That exact path is implemented behind
  `GlassesVisualCapturing` and can feed Lens without vendor logic in SwiftUI.
- Video and local audio recording remain beneath a hardware-validation boundary until their full
  start/stop/result sequences are exercised from the iPhone.
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
- Library now invokes this transport through a provider-neutral media capability, displays explicit
  progress, imports only previously unsynced originals, retains the provider's remote identity for
  deduplication, and always exits transfer mode on completion, cancellation, error or disconnect.
- The AP response sequence still requires its first physical-iPhone validation. The UI therefore
  starts it only after an explicit Sync action; connection alone never enters Wi-Fi transfer mode.

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

- Conversations are stored atomically with after-first-unlock file protection, survive launch, support
  open/new/delete/delete-all, and send a bounded recent context without deleting older local turns.
- Assistant routing currently selects only executors that actually exist: conversation, local
  glasses photo capture, visual question, or clarification. “Click/take/capture a photo” invokes
  the provider-neutral photo capability directly without a cloud profile. It deliberately does
  not pretend that keywords are working weather, sports, places or search tools. Those routes
  should be registered only with real services.
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
- Custom glasses-side wake phrase

The 2026-08-31 official-app HCI capture now confirms restart work type `0x0E` and factory-reset work
type `0x0A`, their acknowledgements, and the following protocol reinitialization. The official OTA
check called `https://www.qlifesnap.com/glasses/app-update/last-ota` and returned “No upgraded
version”; this did not expose an update artifact, signature, bootloader/DFU transition, rollback,
or recovery path. Restart, reset, and OTA remain disabled product placeholders until their first
controlled physical-iPhone recovery tests. Captured destructive codes are evidence, not permission
for the native app to send them automatically.

## Wake phrase, shutter sound and LED

The current `Hey Cyan` detector is glasses-side. Family `0x44` exposes wake listening off/on; the
capture contains no phrase model or phrase text. Safe alternatives before firmware research are:

- keep the physical Assistant button as the dependable no-wake-word entry;
- use the implemented family `0x44` glasses wake-listening toggle, which defaults Off for AD;
- use the implemented phone-owned `Hey AD` Porcupine service as a separate microphone feature. It
  starts only while the app is active, glasses are connected and the feature is enabled; the
  established audio session is intended to remain alive across app switching/lock and suspends
  detection while Speech or spoken output owns the turn;
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
3. With glasses-side voice wake still Off, test the physical Assistant button and phone `Hey AD`
   in foreground, another app, and locked-screen states. Verify two consecutive locked-screen turns
   so wake listening must resume after Speech/TTS.
4. Say “Hey AD, click a photo” and verify the local photo executor, acknowledgement, and physical
   media count. Then run one Lens capture and verify the `0x73` → sequential `0xFD` JPEG path.
5. Exercise AP media preparation once without downloading; verify returned mode, credential
   lengths, `0x73/08` address, association result and cleanup.
6. Only after step 5 succeeds, use Library sync to list media and download one small photo. Lock the
   phone once during transfer. Confirm the original is retained, a second sync recognizes it as
   already imported, and success, cancellation, and expiry all leave the glasses AP.
7. Keep OTA, restart and reset disabled during the first foundation session. Validate each later as
   a separate recovery test with the official app available for repair.

## Honest feature execution matrix before the first iPhone/glasses run

| Workflow | Code state | Remaining physical proof |
| --- | --- | --- |
| BLE connect, restore, reconnect, disconnect, forget | Implemented | iPhone pairing and restoration callbacks |
| Phone `Hey AD` → Speech → AI → spoken answer | Implemented/configuration-gated | locked-screen audio transition and battery use |
| Glasses button audio → Opus → Apple Speech → answer | Implemented from captured packets | physical routing, packet continuity, recognition |
| “Click a photo” voice tool | Implemented locally | command acknowledgement and saved media on this pair |
| Lens glasses JPEG intake | Implemented from captured `0x73`/`0xFD` flow | first iPhone capture; general cloud vision still absent |
| Library AP/HTTP sync | Implemented with cleanup/deduplication | returned AP details and lock/suspension behavior |
| Conversations and local Library persistence | Implemented and simulator-tested | device file-protection check after lock |
| Phrase translation and spoken output | Implemented | installed model/voice and route on the iPhone |
| Continuous live translation | Not implemented | requires a non-echoing listen/translate/speak loop |
| Video/audio recording controls | Verified commands, not product-wired | complete start/stop/result state on iPhone |
| Restart, reset, OTA | Disabled placeholders | isolated recovery and artifact-validation sessions |
