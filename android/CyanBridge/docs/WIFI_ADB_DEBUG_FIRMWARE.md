# Wi-Fi ADB Debug Firmware — Rationale and Usage

**Date:** 2026-07-16
**Author:** CyanBridge Hardware Team
**Status:** Original hotspot SWU rejected; corrected P2P-only candidate built and awaiting hardware validation

Detailed audit: [`WIFI_ADB_DEBUG_FIRMWARE_AUDIT.md`](WIFI_ADB_DEBUG_FIRMWARE_AUDIT.md)

Corrected candidate build report: `firmware_dump/RESULTS/patched_swu/WIFIAM01G1_tcp_adb_p2p_debug_20260717/BUILD_REPORT.md`

---

## 1. Overview

The file `CyanBridge_wifi_chip_debug.swu` is a patched firmware image intended for the WIFIAM01G1 glasses family. It is intended to enable ADB (Android Debug Bridge) over Wi-Fi, but neither boot-time Wi-Fi association nor TCP ADB has been validated on hardware.

This represents the hardware team's current best understanding of how to access the glasses' V821 Linux system for diagnostics. The intended rootfs changes are small, but the distributed SWU is a full multi-partition update and has not yet been validated on a running device.

The original `CyanBridge_wifi_chip_debug.swu` remains rejected. A separate corrected candidate was rebuilt from the exact stock SWU without the `HeyCyanDebug` hotspot script. It changes only `/etc/init.d/adbd` in each rootfs:

```diff
-#ADB_TRANSPORT_PORT=5555
+export ADB_TRANSPORT_PORT=5555
```

The corrected design reuses the production media-transfer Wi-Fi Direct mode instead of attempting unverified STA association during normal boot.

Corrected candidate:

```text
Path: firmware_dump/RESULTS/patched_swu/WIFIAM01G1_tcp_adb_p2p_debug_20260717/WIFIAM01G1_1.00.28_2603031800_tcp_adb_p2p_debug.swu
Size: 24,714,752 bytes
SHA-256: 881b0620aa8b8e7d13bbf03e517dfe002855fdc9ff000abc217774698ca9de98
```

Static package verification passed, but this candidate is still lab-only until the hardware team confirms recovery, active NOR/SDNAND selection, TCP bind/authentication behavior, and persistent `/etc` behavior.

---

## 2. What the Patch Does

### 2.1 Changes

The intended content change modifies **two init scripts** in each Linux root filesystem variant.

| File | Change | Purpose |
|---|---|---|
| `etc/init.d/adbd` | Assigns `ADB_TRANSPORT_PORT=5555` | Intended to enable TCP ADB, but ineffective because this firmware's `rc.common` discards `procd_set_param env` |
| `etc/init.d/S98debug_wifi` (new file) | Tries `wifi -c HeyCyanDebug debug1234` on boot | Attempts debug-hotspot association so TCP ADB may become reachable |

### 2.2 Full-SWU Scope

The non-rootfs payloads are byte-for-byte copies from the matching base firmware. The selected NOR or SDNAND set is still rewritten by this SWU; packaged `uboot` members are not selected by its active manifest entries. Its `sw-description` installs:

- NOR kernel, rootfs, RISC-V image, user image, and boot0.
- SDNAND kernel, rootfs, RISC-V image, user image, and boot0.

Consequently, this is not a rootfs-only flash. A power loss, target mismatch, corrupt package, or incorrect base image can affect boot-critical partitions even though only two files were intentionally edited. Before hardware use, retain the exact stock recovery image and verify every unchanged payload against the base firmware.

### 2.3 How It Works at Boot

```
1. Glasses power on, bootloader loads kernel
2. Kernel mounts rootfs (squashfs, read-only)
3. `rcS` starts `adbd` because `load_script.conf` contains `adbd`
4. `rc.final` executes files matching `/etc/init.d/S??*`
5. `S50wifidaemon` runs, but the current file is a no-op and does not start `wifi_daemon`
6. `S98debug_wifi` runs (new):
   a. Waits 5 seconds for wifi_daemon to initialize
   b. Checks if already connected to a network
   c. If not connected, tries: wifi -c HeyCyanDebug debug1234
   d. If association succeeds: the glasses should obtain an address on the network
   e. If association does not succeed: the script times out and returns success; effects on the existing Wi-Fi state remain unverified
7. `adbd` starts with its existing USB setup, but TCP port 5555 is not enabled because `ADB_TRANSPORT_PORT` never reaches the daemon process.
```

The rootfs also contains `etc/init.d/wifi_start`, which would start `wifi_daemon` and select STA mode, but its filename does not match the `S??*` pattern and it is not listed in `load_script.conf`. The current automatic-association path is therefore broken. The authoritative replacement startup sequence remains unresolved until the hardware team supplies it and a USB/serial recovery session validates it.

---

## 3. Why This Approach

### 3.1 The Problem

The glasses run a custom Linux system (Allwinner V821 SoC). The apparent UART test pads are impractical for field access, so network access is the current non-invasive shell-access path under investigation.

### 3.2 Why ADB

The V821 root filesystems include `adbd` and an init script that already configures the USB gadget. The binary contains TCP-transport support, but the current patch does not export `ADB_TRANSPORT_PORT` into the daemon process. This must be corrected before runtime bind/authentication behavior can be tested.

### 3.3 Why a Debug Hotspot

The firmware contains STA-mode tooling, but its boot-time initialization path is not yet confirmed. The patch adds a script that tries to connect to a specific hotspot (`HeyCyanDebug`) on boot. Successful association should make TCP ADB reachable if the daemon and routing are working; failure is intended to return control to boot, but absence of side effects has not been demonstrated.

After the confirmed blockers are corrected and validated, this approach could avoid changing the user's existing access point. Do not assume that a failed hotspot connection has no side effects or that a recovery re-flash will always be available.

### 3.4 Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Full SWU fails or loses power | Unknown | Critical | Use a recoverable lab unit, matching stock recovery image, stable power, and exact-family checks. |
| Wi-Fi stack is not initialized | High until tested | Medium | Verify WLAN module, `wifi_daemon`, STA mode, DHCP, and port 5555 before field use. |
| Normal operation affected | Unknown until tested | Medium | Run boot, BLE, media, P2P, and reboot regressions after flashing. |
| Wrong family/base firmware | Avoidable | Critical | Restrict to the exact WIFIAM01G1 hardware/base version and verify package SHA-256. |
| Security exposure | High if TCP ADB starts | High | Fixed credentials plus potentially unauthenticated/root-capable ADB are lab-only; isolate the AP and re-flash stock firmware afterward. |

---

## 4. Usage

### 4.0 Corrected P2P Debug Flow

The preferred corrected flow does not use `HeyCyanDebug` credentials:

1. Install a debug CyanBridge build and use only a recoverable lab device.
2. Flash the corrected candidate only after hardware-team approval and recovery rehearsal.
3. Force-stop the official HeyCyan app.
4. In CyanBridge, open Glasses → Developer tools → ADB over glasses Wi-Fi Direct.
5. Read and explicitly acknowledge the destructive-risk warning.
6. CyanBridge registers the BLE IP listener and starts reset-disabled P2P discovery.
7. CyanBridge sends exactly one confirmed transfer-entry command `[0x02,0x01,0x04]`.
8. The production transfer-mode path loads WLAN, starts P2P, and reports the glasses IP through notify `0x08`.
9. CyanBridge probes `<glasses-ip>:5555` through the selected P2P `Network.socketFactory`.
10. If reachable, CyanBridge starts a loopback-only phone relay on `127.0.0.1:15555`.
11. On the PC, forward through the phone's USB-debugging ADB transport:

```bash
adb -s <phone-serial> forward tcp:15555 tcp:15555
adb connect 127.0.0.1:15555
adb -s 127.0.0.1:15555 shell
```

12. Press Stop before leaving the screen. CyanBridge closes the relay, sends confirmed exit-transfer command `[0x02,0x01,0x09]` at most once after entry acknowledgement, and tears down P2P.
13. Remove a stale host forward if necessary:

```bash
adb -s <phone-serial> forward --remove tcp:15555
```

The real controller and relay exist only in `app/src/debug`; the release source set contains an inert controller and release DEX verification found no relay or debug-controller markers.

### 4.1 Prerequisites

- For the rejected hotspot design only: a phone or portable hotspot using `HeyCyanDebug` / `debug1234`
- A PC on the same network with `adb` installed
- The corrected P2P-only candidate above, reviewed and approved by the hardware team; do not flash the original hotspot artifact

### 4.2 Flashing

The patched SWU is intended for the V821 Wi-Fi/P2P pull-mode OTA mechanism:

1. **Wi-Fi OTA (lab path, not yet hardware-validated):** CyanBridge can serve the SWU to the glasses over P2P. The package instructs `swupdate` to rewrite the selected NOR or SDNAND image set; do not infer that both rootfs copies are flashed in one run.

The V821 `.swu` path is Wi-Fi/P2P pull-mode OTA. Do not send this SWU through the JieLi BLE DFU path; that path expects a matching `.bin` firmware image for the BLE chip.

Before flashing:

1. Confirm the glasses report the WIFIAM01G1 family and the expected base firmware.
2. Record and independently verify the corrected rebuild's new SHA-256. Do not reuse the rejected artifact's hash listed below.
3. Keep the matching stock SWU and a hardware-team-approved recovery procedure available.
4. Use stable external power and a recoverable lab unit.
5. Turn off the `HeyCyanDebug` hotspot during OTA so it cannot conflict with the phone's Wi-Fi Direct routing.
6. Force-stop the official HeyCyan app so it cannot compete for BLE/P2P state.
7. Do not cancel or power-cycle after the glasses begin downloading or flashing.

### 4.3 Connecting

1. Enable the `HeyCyanDebug` hotspot on your phone (SSID: `HeyCyanDebug`, password: `debug1234`, 2.4GHz band, WPA2)
2. Power on the glasses (or reboot them)
3. Wait until the hotspot reports a new client/address; do not assume a fixed boot or DHCP time
4. Connect the PC's Wi-Fi adapter to the same `HeyCyanDebug` hotspot.
5. Find the glasses' IP address in the hotspot client list. If needed, inspect neighbors from the USB-connected phone with `adb -s <phone-serial> shell ip neigh show`.
6. From the PC on that same hotspot:
   ```bash
   adb connect <glasses-ip>:5555
    adb -s <glasses-ip>:5555 shell
   ```
7. Select the network device explicitly and run the validation checks below.

```bash
adb devices -l
adb -s <glasses-ip>:5555 shell 'grep "^Uid:" /proc/self/status; grep "^Gid:" /proc/self/status; uname -a; cat /proc/cmdline'
adb -s <glasses-ip>:5555 shell 'ifconfig -a; route -n; ps | grep -E "adbd|wifi"'
adb -s <glasses-ip>:5555 shell 'netstat -tlnp | grep 5555'
adb -s <glasses-ip>:5555 shell 'grep ADB_TRANSPORT_PORT /etc/init.d/adbd; ls -l /etc/init.d/S98debug_wifi'
adb -s <glasses-ip>:5555 shell 'P="$(pidof adbd)"; tr "\000" "\n" < "/proc/$P/environ" | grep "^ADB_TRANSPORT_PORT="'
```

Expected minimum result for a corrected image: a shell whose UID is recorded rather than assumed, a DHCP address on `wlan0`, `adbd` listening on TCP 5555, and the two patched scripts present. If any check fails, stop and collect boot logs through the hardware team's recovery/debug interface before changing more firmware.

### 4.4 PC Networking

The PC should join the same hotspot over Wi-Fi. A phone hotspot commonly places all Wi-Fi clients on one subnet, although some phones enable client isolation; test reachability on the actual phone.

USB tethering is not a reliable substitute. It normally creates a second downstream subnet and Android commonly blocks forwarding between USB-tethered clients and Wi-Fi-hotspot clients. `bindProcessToNetwork()` in CyanBridge affects only that Android app and does not create a PC-to-glasses route.

Preferred options, in order:

1. Connect the PC directly to the phone's `HeyCyanDebug` hotspot over Wi-Fi.
2. Use a travel router or PC-hosted 2.4 GHz WPA2 hotspot with the configured SSID/password, then join the PC to that LAN.
3. If USB is the only PC link, run `adb connect <glasses-ip>:5555` from Termux on the hotspot phone, or use an explicitly configured user-space TCP relay. Do not assume stock USB tethering forwards the connection.

### 4.5 What You Can Do With the Shell

```bash
# List LED devices (investigate camera LED control)
ls /sys/class/leds/

# Monitor LED state in real time
while true; do cat /sys/class/leds/*/brightness; usleep 100000; done

# Check current system mode
cat /sys/kernel/aglink_mode

# List running processes
ps

# Check network interfaces and ports
netstat -tlnp
ifconfig

# Only while recording/transfer/OTA are stopped: check space, then dump the V821 RISC-V partition
df -h /mnt/UDISK
dd if=/dev/mtdblock4 of=/mnt/UDISK/v821_riscv.bin bs=64K

# Check audio devices
cat /proc/asound/cards

# Explore the filesystem
ls /usr/sbin/
ls /bin/
ls /mnt/UDISK/

# Read the current mode; do not write an inferred value during initial validation
cat /sys/kernel/aglink_mode
```

Writing `8` to `aglink_mode` alone is not a validated livestream launch procedure. `rtc_init.sh` reads the mode and launches the corresponding process when the dispatcher runs; changing the sysfs value does not by itself prove the dispatcher ran, and invoking it over an active mode may create resource conflicts. Use only the hardware team's confirmed entry and exit procedure.

### 4.6 Disabling Debug Access

The intended removal path is to re-flash the exact matching stock firmware (without the debug patches), then verify that the TCP listener is absent and `S98debug_wifi` no longer exists. Recovery availability is not yet proven, so this must not be treated as a guaranteed rollback.

---

## 5. Compatibility

### 5.1 Target Family

`CyanBridge_wifi_chip_debug.swu` is based on `WIFIAM01G1_1.00.28_2603031800.swu` and must be treated as limited to the exact matching **WIFIAM01G1** hardware/base version. Compatibility has not been established by a device flash.

Other families (WIFIA03, WIFIAM01C, WIFIAM01W, etc.) require their own reviewed SWU built from the matching base firmware. The same two intended content edits may apply, but scripts, rootfs contents, and package layout must be checked per family.

### 5.2 File Details

| Field | Value |
|---|---|
| Filename | `CyanBridge_wifi_chip_debug.swu` (rejected artifact; do not flash) |
| Base firmware | `WIFIAM01G1_1.00.28_2603031800` |
| Size | 24,632,845 bytes |
| SHA256 | `065ee9342751ae4b34c13a330564b85bf7a96132157d126a84bb7d6ee6cf205a` (identifies rejected artifact only) |
| Format | ASCII cpio archive (SVR4 with CRC) — standard SWUpdate format |
| Target platform | `v821-aiglass/generic` (Allwinner V821) |

---

## 6. Known Limitations

1. **Automatic association is broken:** Normal/idle boot does not load WLAN, `wifi_start` has no observed caller, `S50wifidaemon` is a no-op, and `S98debug_wifi` uses an absent `ip` command.

2. **Band support:** Hardware-team guidance says to use 2.4 GHz; static inspection in this audit did not independently establish radio-band capability.

3. **TCP ADB environment is broken:** The patched variable is not inherited by `adbd`, so the current image does not request TCP ADB at runtime.

4. **No persistent debug toggle:** A corrected rootfs would request ADB TCP and run the hotspot script on every boot. A future enhancement should gate it with a UDISK flag.

5. **Single-family for now:** Only WIFIAM01G1 has been analyzed. Each family requires its own metadata-faithful rebuild and audit.

---

## 7. Future Improvements

The following enhancements are being considered and may be forwarded to the CyanBridge engineering team:

- **UDISK trigger script:** Deploy a script via ADB that checks for a flag file (`/mnt/UDISK/adb_enabled`) on boot. If the flag exists, enable ADB TCP. If not, keep it disabled. This allows toggling debug access without reflashing.

- **BLE-triggered ADB:** Explore whether a BLE command can dynamically enable ADB TCP at runtime, avoiding the need for a dedicated hotspot.

- **Per-family patches:** Build debug SWUs for all firmware families (WIFIA03, WIFIAM01C, WIFIAM01W, etc.) using the same init script changes.

- **Additional debug tools:** Bundle lightweight diagnostic tools (e.g., `strace`, `tcpdump`) in the UDISK overlay for advanced debugging.
- **Verified WLAN startup:** Replace assumptions about `wifi_start` with an init path proven on hardware, including module loading, daemon readiness, STA selection, DHCP, and retry logging.
- **Package verification:** Record hashes for every SWU member and compare all non-rootfs payloads with the exact stock base before release.
