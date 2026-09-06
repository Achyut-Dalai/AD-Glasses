# iOS HeyCyan hardware-validation runbook

This runbook is deliberately conservative. It uses the official HeyCyan app and CyanBridge as
known-working references, observes first, and enables one iOS capability only after the relevant
request and response are identified in a fresh trace.

## Scope

In scope:

- BLE discovery, GATT connection, notification subscription, disconnect and reconnect
- battery and device information
- photo, video, audio-recording and AI-photo controls already evidenced in this repository
- BLE audio packet capture and Opus header/state-machine identification
- BLE-coordinated Wi-Fi AP transfer and the current HTTP media contract

Out of scope:

- firmware/OTA and bootloader operations
- factory reset
- ANC/noise controls
- livestream
- any command or response field not present in repository evidence or a new captured trace

## Before a session

1. Charge the glasses and phone sufficiently.
2. Keep only one controlling app connected at a time. Force-stop AD Glasses/CyanBridge while the
   official app is the reference, and disconnect the official app before testing CyanBridge or iOS.
3. Connect the Android phone over USB, unlock it, choose file transfer if Android asks, and approve
   the Mac's USB-debugging key.
4. Confirm `adb devices -l` reports the phone as `device`, not `offline` or `unauthorized`.
5. Record the glasses Bluetooth name, phone time, app name/version and glasses firmware shown by
   the reference app.
6. Start a new local trace for each action. Never mix several button presses into one trace.

## Passive baseline

Capture these transitions without issuing any experimental command:

1. Official app closed, glasses powered on.
2. Official app launched and automatically reconnecting.
3. Stable connected state for at least 30 seconds.
4. Official app disconnect, followed by reconnect.
5. Glasses powered off unexpectedly, followed by powered on and automatic reconnect.

For every transition retain timestamps, GATT service/characteristic identifiers, notification
enablement, MTU/write sizes, raw notification bytes, and Android log messages. Secrets such as Cloud
AI keys are never part of the capture.

## One-action reference captures

Run each action once from the official app, return to an idle connected state, then repeat once in
CyanBridge. Capture both TX and RX bytes and note the visible result:

1. Refresh battery/device information.
2. Take one photo.
3. Start video, wait five seconds, stop video.
4. Start audio recording, wait five seconds, stop audio recording.
5. Request one AI photo at a single known quality.
6. Open the media library/transfer flow, record the BLE transition into Wi-Fi mode, association
   details provided by the glasses, local IP information, HTTP requests/responses, and cleanup.
7. Start and stop one glasses-microphone Assistant interaction, retaining raw BLE audio packets for
   offline parser analysis.

Do not repeat an action rapidly. If the glasses stop responding, stop the session, close the app,
power-cycle normally, and review the last trace before any further write.

## Promotion rule for iOS

A feature becomes user-facing only when all of these are true:

- the request bytes match repository evidence and a current hardware trace;
- the response/notification that represents success, failure and completion is understood;
- disconnect, cancellation, timeout and retry behavior are defined;
- repeated use does not leave the glasses in capture or transfer mode;
- the provider exposes the capability and common SwiftUI code uses only that capability interface.

Unknown fields remain raw diagnostic events. They are not converted into battery values, statuses,
filenames, IP addresses or success messages by guesswork.

## iOS connection matrix

Validate on a physical iPhone after the passive reference captures:

| Case | Expected result |
| --- | --- |
| First scan and connect | Only a device proving the verified HeyCyan service is offered; both services and all four required characteristics become ready. |
| User disconnect | BLE connection closes, reconnect timer stays off, UI becomes disconnected. |
| Unexpected range loss | Pending requests fail; bounded backoff reconnect begins. |
| Glasses return in range | GATT and notifications are rediscovered before the provider reports connected. |
| App relaunch | Saved peripheral is retrieved and reconnected without presenting an unrelated BLE device. |
| Bluetooth off/on | App reports unavailable, fails pending work, and resumes only the previously intended connection. |
| Wi-Fi media transfer | BLE remains connected while iPhone temporarily joins the glasses AP; HTTP is local-only; cleanup leaves the AP. |
| Transfer interrupted | Local association is abandoned and no speculative cleanup write is sent over a lost BLE session. |

## Trace handoff

For each action, retain:

- action name and exact timestamp range;
- official HeyCyan/CyanBridge/app version and glasses firmware;
- GATT channel and direction;
- complete hex payload, with secrets redacted only when they are credentials rather than protocol;
- observed UI/device result;
- whether the action was repeated and whether bytes were identical.

The iOS Diagnostics export is an opt-in, bounded JSONL trace intended for this comparison. Because
raw packets may contain device or glasses-network material, treat the export as sensitive and share
it only with trusted recipients. Clear it between cases so a response cannot be attributed to the
wrong action.
