# Hardware validation status

Status as of 2026-08-15: the owner does not currently have the glasses and expects access in approximately one to two weeks.

Until hardware is available:

- retain upstream-derived BLE, Wi-Fi Direct, media-transfer, capture, recording and OTA implementations as the provisional source of behavior;
- do not rewrite a protocol path merely because it cannot be exercised today;
- use fake repositories and deterministic fixtures for UI/prototype testing;
- label hardware-dependent test evidence as Pending physical validation;
- do not claim that AD Glasses has verified connection reliability, battery behavior, media transfer, capture, recording, display output or firmware safety;
- continue build, unit, state-machine and static integration checks that do not require hardware;
- keep firmware code and destructive hardware commands unreachable from the dummy prototype.

When the glasses arrive, validate in this order:

1. BLE scan, family detection, bind and reconnect;
2. connection state, battery/storage/version reads;
3. manual photo, video and onboard audio commands;
4. BLE-triggered Wi-Fi Direct media listing/download and duplicate handling;
5. voice/image question triggers and audio routing;
6. Meeting, captions, translation, Auto Audio and Visual Diary one at a time;
7. long-running conflicts, interruption and recovery;
8. firmware read-only version/compatibility probes;
9. firmware flashing only after the separate preflight/recovery safety gate.

Record device model, firmware versions, Android device/OS, exact steps, logs and outcome for every physical validation run. A passed upstream test is useful evidence but is not a substitute for testing this app build on the owner's hardware.
