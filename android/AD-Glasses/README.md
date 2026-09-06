# AD Glasses — Android reboot

This directory is the clean Android implementation of AD Glasses. It intentionally does not inherit the previous Android application architecture.

## Product baseline

- Kotlin + Jetpack Compose only.
- iOS `main` is the visual/product reference, except the active conversation transcript, which uses a calmer reading-first Android layout instead of chat bubbles.
- HeyCyan transport is implemented from the verified production protocol in `docs/heycyan/`, not from neighboring command guesses.
- The retained vendor AAR lives at `../glasses_sdk_20250723_v01.aar` as a reference artifact. The application does not need it for the verified raw protocol foundation.
- Destructive firmware, factory-reset, restart, and OTA operations are deliberately not executable.

## Build

Use JDK 17 and Android SDK 37.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## What works in this foundation

- verified HeyCyan BLE scan/connect and both GATT notification families;
- production `0xBC` framing, little-endian payload length, payload-only CRC-16/MODBUS, CRC-valid stream resynchronization;
- post-GATT initialization with time, battery, device-info, volume read and Classic Bluetooth request;
- photo, video, glasses-local audio recording and AI-photo control requests;
- glasses Assistant start/end events and preservation of fixed 40-byte `0x59` Opus packets;
- AP media preparation from matched work-type `04` credentials plus device IP event, local-only Wi-Fi request and HTTP bound to the returned Android `Network`;
- `/files/media.config` and safe `/files/<name>` reads;
- foreground connected-device service with remembered reconnect;
- NotificationListenerService integration;
- call and SMS adapters with permission-aware dialer/composer fallbacks;
- on-device ML Kit translation;
- Android system TTS provider with a clean seam for Sherpa-ONNX/Kokoro;
- local conversation persistence and Compose UI.

## Next hardware-validation gates

1. Validate BLE initialization and capture commands on the connected Samsung.
2. Record the actual work-type `04` response on Android and validate AP versus P2P choice.
3. Add the production Android Wi-Fi Direct path behind the same network-session interface.
4. Feed `0x59` Opus into the selected phone-optimized decoder and speech pipeline.
5. Add Sherpa-ONNX + Kokoro as the high-quality offline TTS provider after profiling real-time factor, memory and thermals on the target phone.
6. Turn translation into a full listen → segment → translate → speak → resume loop, with echo suppression so AD does not transcribe its own speech.
7. Promote notification, call and text tools into Assistant routing only after explicit permissions and contact resolution are in place.

The app is private/sideloaded, but Android operating-system permission, role, foreground-service and background-start rules still apply even when Play Store policy does not.
