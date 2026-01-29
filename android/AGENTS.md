# Codex Agent Guide for HeyCyan Smart Glasses (Android)

This file describes how future Codex agents should work in this repository, with a focus on reverse‑engineering and improving the Android demo app for the HeyCyan (AIMB‑G3) smart glasses.

## Repository layout

- `CyanBridge/` – Android sample app project (published app name: **CyanBridge**, `applicationId`: `com.fersaiyan.cyanbridge`) that uses the vendor SDK (`glasses_sdk_*.aar`). This is the main project we modify and build.
- `HeyCyanOfficialApp/` – decompiled sources/resources from the Play‑store HeyCyan app. Use this only as a reference when we need to understand how the vendor app drives the SDK.
- `glasses_sdk_*.aar` – closed‑source SDK used by both apps.

## Current goal

- Make the **BLE+WiFi P2P Data Download** button in the sample app behave like the corresponding feature in the official HeyCyan app:
  - Glasses should enter the same “data transfer” mode they use with HeyCyan.
  - The phone should fetch `media.config` and then download photos/videos to local storage.
- Secondary, longer‑term goal: understand and, if possible, record/dump **future OTA updates**:
  - Identify OTA endpoints (servers, URLs, request formats) used by the official app.
  - Understand how OTA binaries are transferred to the glasses over BLE/WiFi.

## Status and key findings

- **BLE control & reporting**:
  - Battery, volume, and media‑count APIs in `MainActivity` are working and produce correct values.
  - The device sends various notify frames where `loadData[6]` is a “type” byte:
    - `0x05` – battery, `0x02` – AI/thumbnail path, `0x12` – volume changes, etc.
    - `0x08` – sometimes carries the device Wi‑Fi IP; we decode this into IPv4.
    - `0x09` – P2P/WiFi error; `loadData[7] == -1` (`0xFF` → 255) indicates a retryable P2P failure.

- **P2P / Wi‑Fi Direct**:
  - The sample’s P2P logic lives in `CyanBridge/app/src/main/java/com/fersaiyan/cyanbridge/ui/wifi/p2p/WifiP2pManagerSingleton.kt`.
  - We temporarily edited this file, but it is now **back to the original vendor implementation** to avoid subtle behavior differences.
  - The singleton:
    - Initializes a `WifiP2pManager.Channel`.
    - Handles `discoverPeers`, `connect`, and `requestConnectionInfo`.
    - Calls `resetDeviceP2p()` on internal discovery timeout.
  - `resetDeviceP2p()` in the original code *only logs*; the real “reset P2P” command `[2,1,15]` is issued from the decompiled HeyCyan code (e.g., `PictureFragment` / OTA flows).

- **What currently works / doesn’t work**:
  - The demo app **does** send commands that trigger the glasses’ “data transfer” LED patterns and P2P activity.
  - At least once in the past, the demo app likely put the glasses into transfer mode and the official HeyCyan app (also installed) silently picked up the session and downloaded media. This explains why:
    - The demo UI said “images not downloaded”, but HeyCyan immediately showed new photos.
  - With the current code:
    - We see P2P retries and `0x09` error `255`, plus `resetDeviceP2p` callbacks.
    - We often see **non‑IP** `0x08` frames (e.g., `...,8,0,99,-57,1,2,0,...`) but not always the IP‑bearing `0x08` with bytes like `-64,-88,49,40` (192.168.49.40).
    - Our HTTP downloader (`downloadMediaList` / `downloadAllJpgFiles`) currently does **not** run because we never reach the “have valid IP + stable P2P” condition.

## Development Environment

- **Java Requirements**: The project (specifically the Android Gradle Plugin 8.12.1) requires **Java 17+** to build.
- **JDK Location**: Use the JDK bundled with Android Studio, found at:
  - `/opt/android-studio/jbr` (Java 21)
- **Build Command**: Always set `JAVA_HOME` when running Gradle:
  ```bash
  JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
  ```

## Logcat conventions

When investigating or reproducing behavior, prefer the following tags:

- `DataDownload` – all our high‑level logging for the BLE+WiFi data‑download flow in `MainActivity`.
- `DeviceNotify` – decoded glasses notify frames (battery, Wi‑Fi IP, P2P errors, etc.).
- `WifiP2pManagerSingleton` – P2P lifecycle logs (init, discovery, connect, timeouts).
- `WifiP2pBroadcastReceiver` – raw P2P broadcasts (peers, connection state).
- `LDHMethods` – reflection dump of all `LargeDataHandler` methods (used once to discover SDK capabilities).
- `BleIpBridge` – raw BLE payloads and any IPv4 addresses detected via regex.

Typical command used in this project:

```bash
adb logcat -d -s DataDownload DeviceNotify WifiP2pManagerSingleton WifiP2pBroadcastReceiver BleIpBridge LDHMethods
```

## How to compare official vs. sample app behavior

1. **Find relevant decompiled classes**
   - In `HeyCyanOfficialApp/`, search with `rg` or your IDE for:
     - `LargeDataHandler.getInstance().glassesControl(` – to see how official flows drive the glasses.
     - `writeIpToSoc`, `syncPictureThumbnails`, `syncHeartBeat`, etc.
     - UI entrypoints like `PictureFragment`, `AlbumDepository`, or OTA activities that handle imports.
   - Map the commands (payload arrays) and the order in which they’re called.

2. **Compare P2P controller**
   - Look for the vendor `WifiP2pManagerSingleton` in `HeyCyanOfficialApp` and ensure:
     - Intent actions (`WIFI_P2P_CONNECTION_STATE_CHANGE_ACTION` vs. `WIFI_P2P_CONNECTION_CHANGED_ACTION`).
     - Retry logic (`discoveryTimeOut`, `connectTimeOut`) matches what we run in the sample.
   - Confirm where the vendor app actually calls `resetDeviceP2p()` and where it sends `[2,1,15]` over BLE.

3. **Compare notify handling**
   - In the decompiled app, locate the `GlassesDeviceNotifyListener` used for album/import flows.
   - See how they interpret `loadData[6] == 0x08` and `0x09`:
     - When do they treat errors as fatal?
     - When do they just retry P2P and keep waiting for an IP?

4. **Compare HTTP behavior**
   - Search `\"media.config\"` or `\"/files/\"` in `HeyCyanOfficialApp`:
     - Identify the exact URLs, timeouts, and error handling for media and OTA downloads.
   - Match our `downloadMediaList`/`AlbumDownloader` behavior to those URLs and paths.

## Investigation hints / pitfalls

- **Do not over‑edit the P2P layer**:
  - Keep `WifiP2pManagerSingleton.kt` behavior as close as possible to the vendor version.
  - If you must experiment, prefer *adding* logs or small hooks rather than changing connection/discovery logic.

- **Beware of IP sources**:
  - The glasses can communicate the IP in multiple ways:
    - As a dedicated 0x08 notify frame.
    - Embedded as text/bytes in BLE notifications that `BleIpBridge` can parse.
  - When starting HTTP, prefer device‑reported IPs (`0x08` or `BleIpBridge`) over hard‑coded fallbacks.

- **Error 255 (`0xFF`) is noisy, not always fatal**:
  - Both our sample and the official app regularly see `P2P/WiFi error 255` via `0x09`.
  - The vendor app handles this by resetting P2P and continuing; it still later receives the Wi‑Fi IP and downloads successfully.
  - Don’t treat this as “abort everything”; instead, log and rely on subsequent IP+P2P success signals.

- **Official app may piggy‑back on sample’s state**:
  - We’ve observed cases where:
    - The demo app appears to trigger download mode on the glasses.
    - The official HeyCyan app (running in the background) actually performs the HTTP transfer and shows the new media.
  - When testing our changes, ensure the official app is either:
    - Force‑stopped, if we want to confirm the sample can download on its own, or
    - Intentionally left running, if we’re trying to see cooperative behavior.

## OTA investigation notes

- OTP/OTA support is present in the SDK and official app:
  - Look for classes like `OTAActivity`, `startSocOtaServer`, or HTTP URLs containing firmware filenames.
  - `writeIpToSoc("http://<ip>:8080/<firmwareName>", ...)` is used to tell the glasses where to fetch OTA data.
- For future work on OTA logging/dumping:
  - Identify OTA configuration endpoints and firmware download URLs.
  - Log the full HTTP requests (host, path, headers) from the official app.
  - Observe how OTA binaries are chunked and written to the glasses via BLE or Wi‑Fi (glassesControl/BigData handlers).

### OTA HTTP APIs (current understanding)

- Base API host (from decompiled app + MITM):
  - `https://www.qlifesnap.com/glasses/`
- Relevant endpoints we have observed via MITM:
  - `POST /glasses/encryption/getKeys`
  - `GET  /glasses/device/scanConfig?app=HeyCyan`
  - `POST /glasses/app-update/appLastVersion`
  - `POST /glasses/app-update/last-ota`
  - `POST /glasses/app-update/last-ota/china`
- The OTA metadata call uses a `LastOtaRequest` JSON body:

  ```jsonc
  {
    "appId": 1,
    "country": "US",          // or "CN" for the China endpoint
    "dev": 2,
    "hardwareVersion": "WIFIAM01G1_V9.2",
    "mac": "C4:E3:BF:C3:A4:02",
    "os": 1,
    "romVersion": "WIFIAM01G1_1.00.23_2510111600"
  }
  ```

- The server response shape when **no update is available** (what we see today) is:

  ```json
  { "message": "No upgraded version", "retCode": 60001 }
  ```

  When an update exists, we expect a success `retCode` and a `downloadUrl` pointing at an `.swu` in the `qcwxfactory.oss-cn-beijing.aliyuncs.com` bucket. We have not yet observed such a response for the current glasses firmware.

### Example curl for `last-ota`

> NOTE: Tokens are short‑lived account secrets. The example below uses a token captured on 2026‑01‑23 and is likely expired; future agents should capture their **own** token via MITM (see next section) and substitute it.

```bash
curl -v \
  -H 'Content-Type: application/json; charset=UTF-8' \
  -H 'token: 15ef6eb5403406c1da0dc4a4defa2ea1' \
  --data '{"appId":1,"country":"US","dev":2,"hardwareVersion":"WIFIAM01G1_V9.2","mac":"C4:E3:BF:C3:A4:02","os":1,"romVersion":"WIFIAM01G1_1.00.23_2510111600"}' \
  'https://www.qlifesnap.com/glasses/app-update/last-ota'
```

On the current firmware this returns:

```json
{"message":"No upgraded version","retCode":60001}
```

Changing `country` (`US` ↔ `CN`) or tweaking `romVersion` (e.g. pretending to be older) did **not** produce a `downloadUrl`, which strongly suggests the backend decides “latest vs. not” based on server‑side state, not the client‑supplied version string.

### OSS bucket and `.swu` downloads

- The official app’s debug code uses a pattern like:

  ```text
  https://qcwxfactory.oss-cn-beijing.aliyuncs.com/bin/glasses/<wifiHwVersion>.swu
  ```

- We attempted:

  ```bash
  curl -L -o WIFIAM01G1_V9.2.swu \
    'https://qcwxfactory.oss-cn-beijing.aliyuncs.com/bin/glasses/WIFIAM01G1_V9.2.swu'
  ```

  and received an XML `AccessDenied` error from OSS (“no right to access this object because of bucket acl”), which means these objects require a signed or otherwise authorized URL coming from the OTA API (`downloadUrl` in a successful `last-ota` response).

- Until the server actually advertises a Wi‑Fi OTA (i.e. `last-ota` returns a success `retCode` plus `downloadUrl`), we cannot legitimately pull a real `.swu` for this hardware from the vendor’s infrastructure.

### MITM workflow for capturing OTA traffic and tokens

- We successfully used **mitmproxy** plus a Magisk module to intercept HeyCyan’s HTTPS traffic:
  - Magisk module: `Always Trust User Certificates` (or any equivalent “trust user CAs as system CAs” module).
  - Install the mitmproxy CA as a **CA certificate**, not as a Wi‑Fi/identity cert:
    - Visit `http://mitm.it/` in the phone browser (with the proxy configured).
    - Download the Android CA and install it under security / trusted credentials.
  - Configure the phone’s Wi‑Fi proxy to point at the PC running mitmproxy:

    ```text
    Proxy host: <PC LAN IP>   # e.g. 192.168.1.50
    Proxy port: 8080
    ```

  - Turn **mobile data off** so HeyCyan can’t bypass via LTE.
  - Run mitmproxy on the PC:

    ```bash
    mitmproxy --listen-port 8080
    ```

  - In mitmproxy, filter for the vendor domain:

    ```text
    f
    ~d qlifesnap.com
    ```

  - Force‑stop HeyCyan, then reopen it and navigate to the About / OTA screen. You should see:
    - `POST /glasses/encryption/getKeys`
    - `GET /glasses/device/scanConfig?app=HeyCyan`
    - `POST /glasses/app-update/last-ota` (and sometimes `/last-ota/china`).

- The `token` header used by Retrofit is visible in these flows. This is the same value that `QcRetrofitClient` injects from `UserConfig.userToken`. Because user data in `/data/data/com.glasssutdio.wear` is stored in encrypted MMKV, MITM is currently the most practical way to retrieve a working token.

#### Pitfalls we hit with MITM

- **Wrong certificate type**:
  - Installing the mitmproxy cert as a *Wi‑Fi* credential causes TLS handshake failures and “no Internet” errors. It must be installed as a CA certificate and trusted system‑wide (with the Magisk module).
- **Mixed app behavior**:
  - Some apps (YouTube, Reddit) still reject the MITM cert and show “no connection”. Chrome and HeyCyan do work once the CA is correctly installed.
- **OTA button not always doing a fresh network call**:
  - The “check for update” UI in HeyCyan can display “already latest” even with all radios off, which suggests it sometimes uses cached state rather than hitting `last-ota` on demand. For clean captures, force‑stop the app before opening the OTA screen.

### Local tooling for future `.swu` analysis

- There is a helper script at the Android repo root:
  - `parse_swu.py`
  - Usage:

    ```bash
    cd android
    python parse_swu.py <firmware>.swu
    ```

  - It:
    - Detects obvious container types (gzip/zip/tar/ELF/squashfs).
    - Warns if the file is actually an XML error (e.g. OSS `AccessDenied`).
    - Scans for chip‑related markers (e.g. `JL7018`, `ALLWINNER`, `V821`) to help distinguish JL7018F vs. Allwinner V821L2 payloads.

Once a real `.swu` is available (via a successful `last-ota` response with `downloadUrl`), future agents should:

1. Download it with curl.
2. Run `file` and `parse_swu.py` on it.
3. Use binwalk / custom scripts to carve out the JL7018F and Allwinner V821L2 images.

### Future firmware‑dump directions (high‑level)

If the cloud OTA API continues to return “No upgraded version” for a long time, there are still a few *local* angles that look promising. Treat the notes below as ideas to explore, not as fully baked procedures:

1. **Leverage “pull‑mode” OTA over Wi‑Fi**
   - The SDK path used by the official app includes calls like `writeIpToSoc("http://<ip>:8080/<firmwareName>", ...)` and helpers such as `startSocOtaServer(...)` in `OTAActivity`.
   - Conceptually, this lets the **phone tell the glasses where to fetch an OTA image over HTTP**, using the existing Wi‑Fi P2P link.
   - A future agent could:
     - Run a small HTTP server on the phone/PC (e.g. `python -m http.server 8080`).
     - Place a known test file (dummy `.swu`) in that directory.
     - Call `writeIpToSoc("http://<phone-ip>:8080/dummy.swu", ...)` from a thin wrapper in the demo app or from a test activity modeled after `OTAActivity`.
     - Observe the incoming HTTP requests from the glasses (range/offset patterns, headers, etc.) to better understand how the SoC expects OTA payloads to be structured.
   - This does **not** depend on the vendor OTA servers and should work even when the cloud reports “already up to date”.

2. **Explore LargeDataHandler / BigData op‑codes for diagnostic dumps**
   - The same SDK namespace (`LargeDataHandler`, `BigData*`, etc.) already streams large media files and OTA state over BLE.
   - Decompiled code shows that there are additional op‑codes beyond the ones used in the sample app (some appear to relate to logs or diagnostics).
   - Future work:
     - Use reflection or static analysis on `glasses_sdk_*.aar` and the decompiled HeyCyan app to catalog `LargeDataHandler` methods and their op‑codes.
     - Compare those to how the official app handles crash logs or internal diagnostics (often gated behind “engineer” / debug UI).
     - If safe, add a narrow Kotlin wrapper in the sample app that invokes *documented* diagnostic/dump methods and writes the resulting bytes to storage on the phone for analysis.
   - Keep changes minimal and well‑logged; avoid blindly poking unknown op‑codes on user hardware.

3. **Hardware‑level investigation (ISP / FEL / debug pads)**
   - The headset uses a JL7018F main controller and an Allwinner V821L2 co‑processor; both families are known (from other products) to expose low‑level USB/boot modes for firmware provisioning.
   - PCB‑level investigation (only for experienced hardware folks, with full awareness of warranty and legal implications):
     - Carefully identify any labelled test pads / boot jumpers on the board (e.g. pads near USB‑C that might correspond to “BOOT”, “FEL”, etc.).
     - With the battery disconnected and ESD precautions in place, verify via `lsusb` whether holding certain buttons or shorting documented pads at power‑on exposes a new USB device (indicating a ROM loader or download mode).
     - If such a mode exists, consult chip‑family documentation and community tooling for **read‑only** flash inspection first (before attempting any writes).
   - This is invasive work; it should be treated as a last resort and documented carefully if attempted. Do **not** assume that methods used on other JL/Allwinner boards are safe here without verification.

## General guidance for future Codex agents

- Treat the vendor SDK (`.aar`) and decompiled code as authoritative for protocol details; keep our glue code thin and well‑logged.
- When something works in the official app but not in the sample:
  - First compare **method sequences and payloads** (what SDK calls, in what order).
  - Then compare **state machines** (when they retry, when they reset, when they treat an error as fatal).
- Always capture and reason from **logcat** before changing code; use the tag set above and keep logs alongside any code changes you make for traceability.***
