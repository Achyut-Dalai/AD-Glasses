# Wi-Fi ADB Debug Firmware Audit

**Date:** 2026-07-17
**Artifact:** `AD-Glasses_wifi_chip_debug.swu`
**Target claimed:** `WIFIAM01G1_1.00.28_2603031800`
**Verdict:** **Original hotspot SWU NO-GO; corrected P2P-only candidate statically verified, hardware validation pending**

## 1. Executive Summary

The current debug SWU is structurally readable and its packaged non-rootfs payloads match the exact stock SWU. It should still not be flashed, even on a lab unit, because static inspection confirms that its intended Wi-Fi ADB path will not work reliably and that the rebuilt root filesystems contain unintended metadata/device-node changes.

The main blockers are:

| Severity | Finding | Status |
|---|---|---|
| Blocker | `ADB_TRANSPORT_PORT=5555` is passed through a fake `procd_set_param env` implementation that discards environment variables. `adbd` will not inherit it. | Confirmed |
| Blocker | Normal/idle boot does not load the WLAN module. Only modes 2, 3, 8, and 15 call `load_wlan`. | Confirmed |
| Blocker | `wifi_start` is not executed by the observed boot path and `S50wifidaemon` is a no-op. | Confirmed |
| Blocker | `S98debug_wifi` uses the `ip` command, but neither rootfs packages an `ip` executable or BusyBox symlink. | Confirmed |
| Blocker for SDNAND | Persistent `rootfs_data` may mount over `/etc` and hide the patched scripts; the SDNAND update has no postinstall step to invalidate or migrate it. | Confirmed mechanism; runtime effect requires hardware validation |
| Packaging defect | All rebuilt rootfs objects changed ownership from `0:0` to `1000:1000`. | Confirmed |
| Packaging defect | Stock `/dev/console` character device `5:1` is absent from both rebuilt rootfs images. | Confirmed |
| Recovery blocker | No tested recovery transport or rollback procedure is documented. NOR postinstall deliberately invalidates SDNAND boot records. | Confirmed scripts; hardware recovery unknown |

The original artifact must not be flashed. Its metadata-faithful replacement has now been built as described below, but the hardware team must still review the one-line patch and prove recovery, rootfs selection, and runtime ADB behavior before OTA testing.

## 1.1 Corrected P2P-Only Candidate

A corrected candidate has now been built from the exact stock SWU:

```text
Path: firmware_dump/RESULTS/patched_swu/WIFIAM01G1_tcp_adb_p2p_debug_20260717/WIFIAM01G1_1.00.28_2603031800_tcp_adb_p2p_debug.swu
Size: 24,714,752 bytes
SHA-256: 881b0620aa8b8e7d13bbf03e517dfe002855fdc9ff000abc217774698ca9de98
Build report: firmware_dump/RESULTS/patched_swu/WIFIAM01G1_tcp_adb_p2p_debug_20260717/BUILD_REPORT.md
```

Its only filesystem content change in both `rootfs` and `rootfs_sdnand` is:

```diff
-#ADB_TRANSPORT_PORT=5555
+export ADB_TRANSPORT_PORT=5555
```

The candidate contains no `S98debug_wifi`, hotspot SSID, password, or automatic Wi-Fi action. It preserves stock `0:0` ownership, `/dev/console` `5:1` mode `0600`, modes, symlinks, timestamps, SquashFS settings, CPIO member ordering, and all non-rootfs payload bytes. Its 15-entry MD5 manifest is valid and changes only the two rootfs entries.

Static build status is PASS. Hardware status remains CONDITIONAL/UNVERIFIED because static inspection cannot establish:

- Which rootfs variant the target actually boots or updates.
- Whether persistent `/etc` hides the patched script.
- Whether `adbd` binds TCP 5555 as expected when P2P appears.
- ADB authentication and shell privilege behavior.
- A tested recovery path after interrupted boot-critical writes.

## 1.2 Why Reuse Production P2P

Production media transfer already proves a controlled WLAN path exists:

```text
confirmed BLE transfer command 0x04
  -> aglink mode 2
  -> rtc_init.sh load_wlan
  -> ai_glass_download / libwifimg P2P
  -> BLE notify 0x08 glasses IP
  -> HTTP file server over Wi-Fi Direct
```

This does not validate the separate `wifi_daemon`/STA/`wifi -c` boot path. It does make the existing transfer mode the smallest and best-grounded transport for a debug ADB experiment.

## 1.3 AD Glasses Debug-Only Integration

AD Glasses now contains a source-set-gated ADB-over-P2P workflow:

- Real controller/network/relay implementation exists only under `app/src/debug`.
- `app/src/release` contains an inert implementation with no BLE, P2P, or socket operations.
- A checkbox-backed destructive-risk confirmation is required before startup.
- The workflow acquires an exclusive `WIFI_ADB_DEBUG` glasses session.
- It registers notify listener slot 2 before sending entry.
- It sends only `[0x02,0x01,0x04]` for entry and `[0x02,0x01,0x09]` once for exit.
- P2P discovery explicitly disables the timeout path that sends reset command `0x0F`.
- Only notify `0x08` supplies the glasses IP; notify `0x09` is log-only.
- It selects a unique non-VPN P2P/WFD Android network on the glasses `/24`.
- Probe and relay outbound sockets use that network's `socketFactory`; the process is never globally bound.
- The phone relay listens only on `127.0.0.1:15555` and is intended for `adb forward` over phone USB debugging.
- Stop/lifecycle/BLE/P2P failures close relay sockets and quarantine the session until command/P2P teardown is confirmed.

PC commands after AD Glasses reports `Relay ready`:

```bash
adb -s <phone-serial> forward tcp:15555 tcp:15555
adb connect 127.0.0.1:15555
adb -s 127.0.0.1:15555 shell
```

Cleanup:

```bash
adb -s <phone-serial> forward --remove tcp:15555
```

USB tethering alone does not route the PC into the P2P subnet and is not the supported path. `adb forward` reaches the app's loopback-only relay without exposing port 15555 to LAN, P2P, or tethering peers.

## 2. Artifact Identity

Patched artifact:

```text
Path: android/AD-Glasses/docs/AD-Glasses_wifi_chip_debug.swu
Size: 24,632,845 bytes
SHA-256: 065ee9342751ae4b34c13a330564b85bf7a96132157d126a84bb7d6ee6cf205a
Format: ASCII cpio archive, SVR4 with CRC
```

Exact stock artifact found in the repository:

```text
Path: firmware_dump/Pure_Firmware_Dumps/WIFIAM01G1_1.00.28_2603031800.swu
Size: 24,714,752 bytes
SHA-256: b91e91e2a677bf4211fcc7b94f68fbeb098fa3df89ef76af0fce9a205efeb550
```

Both archives pass CPIO CRC verification. Every packaged non-rootfs payload in the patched SWU is byte-identical to stock, including kernels, RISC-V images, boot0 images, user image, scripts, and `sw-description`. Only `rootfs`, `rootfs_sdnand`, and the generated integrity-list member differ.

This content equality does not make the update rootfs-only. `sw-description` still instructs SWUpdate to rewrite boot-critical members from the selected NOR or SDNAND image set.

## 3. Confirmed Boot Sequence

### 3.1 Early init

The kernels select `init=/files/init`. The relevant sequence in both rootfs variants is:

1. Mount `/proc`, `/tmp`, `/sys`, and devtmpfs on `/dev`.
2. Start `/etc/media/rtc_init.sh` asynchronously.
3. Mount UDISK; NOR then mounts a selected user partition, while SDNAND explicitly skips `mount_user`.
4. On SDNAND first boot, potentially erase SD boot records via `erase_sdnand_boot0`.
5. Mount persistent `rootfs_data` over `/etc` when enabled.
6. Execute BusyBox `/sbin/init`.

Evidence: `firmware_dump/RESULTS/patched_swu/WIFIAM01G1_debug/rootfs_patch/files/init:402-424,480-533`.

The asynchronous `rtc_init.sh` means mode application startup can overlap with later init scripts.

### 3.2 BusyBox init and rcS

`/etc/inittab` invokes `/etc/init.d/rcS boot`. `rcS` then:

1. Runs preboot/init/log/mount helpers.
2. Attempts `rc.modules`, but no `rc.modules` file is packaged.
3. Reads `load_script.conf`, which contains only `adbd`.
4. Starts the `adbd` init script asynchronously.
5. Sources `rc.final`.

`rc.final` scans `/etc/init.d/S??*` in filename order:

```text
S00mpp
S01usb
S50wifidaemon
S98debug_wifi
```

`START=` values do not control this scan. Files under `/etc/rc.d`, including `S97wifi_start`, are not scanned by any observed boot code.

### 3.3 Persistent `/etc`

`/files/init` can mount a persistent `rootfs_data` filesystem over `/etc`. If it contains `etc_complete` and not `etc_need_update`, the newly flashed SquashFS `/etc` is hidden.

NOR postinstall zeros the beginning of `/dev/mtdblock7`, likely invalidating NOR `rootfs_data`. The SDNAND update has no equivalent postinstall operation for `/dev/mmcblk0p11`. Therefore an SDNAND boot can continue using an old `adbd` script and omit the new `S98debug_wifi` even after the new rootfs was flashed.

The intended migration semantics for persistent `/etc` must be supplied by the hardware team.

## 4. WLAN Startup Audit

### 4.1 Driver

Only `v821_smac.ko` is packaged for this family. No `v821_fmac.ko` was found.

Runtime locations:

```text
NOR:    /mnt/UDISK/data/lib/modules/$(uname -r)/v821_smac.ko
SDNAND: /lib/modules/$(uname -r)/v821_smac.ko
```

`rtc_init.sh` is the only explicit boot-relevant WLAN module loader found. It loads WLAN only for these modes:

| Mode | Purpose | WLAN loaded? |
|---:|---|---|
| 0 | Photo | No |
| 1, 10 | Video | No |
| 2 | Download | Yes |
| 3 | OTA | Yes |
| 4 | AI | No |
| 5, 7 | Normal/idle | No |
| 6 | Audio | No |
| 8 | Livestream | Yes |
| 15 | ETF | Yes |

Normal/idle boot therefore does not satisfy the WLAN prerequisite for automatic hotspot association.

### 4.2 Daemon and STA mode

`S50wifidaemon` contains only:

```sh
start() {
    echo "No need to activate wifi_deamon...."
}
```

`wifi_start` would run:

```sh
wifi_daemon
sleep 3
wifi -o sta
```

However, no observed boot path executes `wifi_start` or `/etc/rc.d/S97wifi_start`.

The `wifi` CLI communicates through `/tmp/UNIX_WIFI.domain`. Consequently, `wifi -c` requires all of the following before it runs:

1. `v821_smac.ko` loaded with the correct firmware available.
2. `wifi_daemon` running and its Unix socket ready.
3. STA mode opened with `wifi -o sta`.
4. Association through `wifi -c <ssid> <password>`.
5. DHCP completion through the Wi-Fi manager/`udhcpc` path.

The current `S98debug_wifi` establishes none of the first three prerequisites.

### 4.3 Missing status command

`S98debug_wifi` checks and polls:

```sh
ip addr show wlan0
```

No `ip` executable exists in either rootfs. Available network tools are `ifconfig`, `route`, `netstat`, `ping`, and `udhcpc`.

As packaged, the script cannot detect an existing address or a successful DHCP assignment and will always reach its failure message after the timeout.

## 5. ADB TCP Startup Audit

### 5.1 How adbd starts

`load_script.conf` causes `rcS` to launch `/etc/init.d/adbd boot`. The script configures the USB FunctionFS gadget and eventually executes:

```sh
procd_set_param command /bin/adbd -D
```

The `adbd` binary contains TCP transport, authentication, and root/drop-privilege strings. Static analysis confirms capability exists, but not its bind address, authentication default, or final shell UID.

### 5.2 Why port 5555 is not enabled

The patched script assigns:

```sh
ADB_TRANSPORT_PORT=5555
```

It then calls:

```sh
procd_set_param env ADB_TRANSPORT_PORT="$ADB_TRANSPORT_PORT"
```

This firmware does not contain normal OpenWrt procd behavior. Its `/etc/rc.common` implementation ignores `env` parameters and directly `exec`s the command for `command` parameters:

```sh
env|data|...|oom_score_adj)
    ;;
command)
    exec $@ >/dev/null
    ;;
```

Because `ADB_TRANSPORT_PORT` is not exported, `/bin/adbd -D` does not inherit it. The current modification therefore does not enable TCP ADB.

A corrected launcher must use a real process environment, for example:

```sh
export ADB_TRANSPORT_PORT=5555
procd_set_param command /bin/adbd -D
```

or, more directly under this fake procd implementation:

```sh
ADB_TRANSPORT_PORT=5555 exec /bin/adbd -D
```

The hardware team should confirm the supported form and desired ADB authentication variables before rebuilding.

### 5.3 Items requiring hardware validation

- Whether TCP binds wildcard `0.0.0.0:5555` or another address.
- Whether a listener started before `wlan0` remains reachable after DHCP.
- Whether `ADB_AUTH_ENABLE` must be set and whether `/mnt/UDISK/adb_keys` is used.
- Whether the shell remains UID 0 or drops privileges.
- Whether USB and TCP ADB can coexist on this build.

## 6. Rootfs Repack Defects

### 6.1 Ownership

Stock rootfs objects are owned `0:0`. Every object in both rebuilt rootfs images is owned `1000:1000`, including executables, libraries, configuration, `/etc/shadow`, directories, and symlinks.

Although many processes run as root and file modes are retained, this is an uncontrolled whole-filesystem change and can affect permission checks or future privilege drops.

### 6.2 `/dev/console`

Stock contains:

```text
crw------- 0/0 5,1 /dev/console
```

The rebuilt images contain only an empty `/dev` directory. `/files/init` later mounts devtmpfs, so a console may eventually appear, but the kernel can attempt to open its initial console before userspace mounts devtmpfs. This defect can remove early diagnostics and should be corrected rather than accepted speculatively.

### 6.3 Integrity list

The generated `cpio_item_md5` contains a prose line not present in stock:

```text
=== Updating cpio_item_md5 ===
```

SWUpdate does not reference that member in `sw-description`, but any vendor-side uploader/parser tolerance remains unknown. The rebuilt package should preserve stock format exactly.

## 7. Update Scope and Recovery

### 7.1 Partition set per run

The SWU defines separate `stable,nor` and `stable,sdnand` selections.

NOR installs:

```text
kernel -> /dev/mtdblock3
rootfs -> /dev/mtdblock5
riscv  -> /dev/mtdblock4
user   -> /dev/mmcblk0p13
boot0  -> /dev/mtdblock0
```

SDNAND installs:

```text
kernel_sdnand -> /dev/mmcblk0p4
rootfs_sdnand -> /dev/mmcblk0p9
riscv_sdnand  -> /dev/mmcblk0p7
user           -> /dev/mmcblk0p14
boot0_sdnand   -> awboot0 handler-selected target
```

A single OTA run does not update both rootfs copies. Which selection `ai_glass_ota` chooses must be confirmed for the target device.

### 7.2 Direct writes and rollback

The manifest uses direct writes to primary partitions. It does not prove an atomic A/B switch or automatic rollback. Possession of a stock SWU is not equivalent to having a recovery transport after kernel, rootfs, RISC-V, or boot0 damage.

### 7.3 Destructive postinstall behavior

NOR postinstall performs:

```sh
dd if=/dev/zero of=/dev/mmcblk0 seek=16 bs=512 count=1
dd if=/dev/zero of=/dev/mmcblk0 seek=256 bs=512 count=1
dd if=/dev/zero of=/dev/mtdblock7 seek=0 bs=512 count=640
```

This deliberately invalidates SDNAND boot records and clears the beginning of NOR `rootfs_data`. Therefore SDNAND must not be assumed to remain an automatic fallback after a NOR update.

On an SDNAND first boot, `/files/init` also erases two SD boot records unless `erase_sd_boot0` is already set. The intended boot/recovery model needs hardware-team clarification.

### 7.4 Realistic failure outcomes

| Failure point | Likely state | Recovery confidence |
|---|---|---|
| Before complete download | Existing flash probably unchanged | OTA retry likely, not guaranteed |
| Archive validation failure | Flash may remain unchanged | OTA retry likely |
| Kernel/rootfs/RISC-V/user write | Selected image set may be partially overwritten | Requires bootable V821 or external recovery |
| boot0 write | Boot-critical data may be partial | Hardware ROM/FEL/EFEX/ISP recovery likely required |
| NOR postinstall | SD boot records/rootfs data may be partially erased | No software rollback demonstrated |
| Patched rootfs hidden by persistent `/etc` | Device may boot stock scripts instead of patches | Requires shell or approved `/etc` migration |

No repository evidence proves that BLE alone can recover a nonbooting V821, that USB ADB remains available after a bad update, or that NOR/SDNAND automatically fail over.

## 8. Required Corrections Before Flashing

1. Rebuild both SquashFS images preserving stock owner/group `0:0` for every object.
2. Preserve `/dev/console` as character device major 5, minor 1, mode `0600`.
3. Export `ADB_TRANSPORT_PORT=5555` into the actual `adbd` process environment.
4. Decide and configure ADB authentication/key behavior explicitly.
5. Replace `ip` usage with packaged `ifconfig`/`route` or include a verified `ip` binary.
6. Add the hardware-approved WLAN sequence: module load, daemon readiness, STA open, association, DHCP verification.
7. Decide how persistent `/etc` is migrated for both NOR and SDNAND.
8. Confirm the intended SWUpdate selector for the exact lab device.
9. Remove the extra prose line from `cpio_item_md5`.
10. Preserve stock SquashFS/CPIO metadata and ordering where practical.
11. Establish and exercise a hardware recovery path before OTA testing.
12. Verify no-hotspot boot, normal BLE/media behavior, P2P, and stock reflash on the recoverable lab unit.

## 9. Staged Validation for a Corrected Build

These commands are for a corrected replacement artifact, not the current NO-GO SWU.

### 9.1 Pre-flash static checks

```bash
set -euo pipefail

PATCH="$(realpath /absolute/path/to/corrected_candidate.swu)"
STOCK="$(realpath firmware_dump/Pure_Firmware_Dumps/WIFIAM01G1_1.00.28_2603031800.swu)"
EXPECTED_ADBD_SHA256="REPLACE_WITH_REVIEWED_64_HEX_HASH"
EXPECTED_S98_SHA256="REPLACE_WITH_REVIEWED_64_HEX_HASH"
WORK="$(mktemp -d /tmp/AD Glasses-swu-audit.XXXXXX)"
CANDIDATE="$WORK/candidate"
STOCK_TREE="$WORK/stock"
mkdir -p "$CANDIDATE" "$STOCK_TREE"
trap 'rm -rf -- "$WORK"' EXIT

sha256sum "$PATCH" "$STOCK"
file "$PATCH" "$STOCK"
cpio --only-verify-crc -it < "$PATCH"
cpio -itv < "$PATCH"
cpio -it < "$PATCH" > "$WORK/candidate-members.txt"
cpio -it < "$STOCK" > "$WORK/stock-members.txt"
if grep -Eq '(^/|(^|/)\.\.(/|$))' "$WORK/candidate-members.txt"; then
  echo "Unsafe archive member path"
  exit 1
fi
diff -u "$WORK/stock-members.txt" "$WORK/candidate-members.txt"

[[ "$EXPECTED_ADBD_SHA256" =~ ^[0-9a-f]{64}$ ]] || { echo "Set approved adbd hash"; exit 1; }
[[ "$EXPECTED_S98_SHA256" =~ ^[0-9a-f]{64}$ ]] || { echo "Set approved S98debug_wifi hash"; exit 1; }

(cd "$CANDIDATE" && cpio -idmv --no-absolute-filenames < "$PATCH")
(cd "$STOCK_TREE" && cpio -idmv --no-absolute-filenames < "$STOCK")

[ -s "$CANDIDATE/cpio_item_md5" ] || { echo "Missing or empty cpio_item_md5"; exit 1; }
if grep -Ev '^[0-9a-f]{32}  [^[:space:]]+$' "$CANDIDATE/cpio_item_md5"; then
  echo "Malformed or unexpected cpio_item_md5 content"
  exit 1
fi

grep -v '^cpio_item_md5$' "$WORK/candidate-members.txt" | sort > "$WORK/expected-md5-members.txt"
awk '{print $2}' "$CANDIDATE/cpio_item_md5" | sort > "$WORK/actual-md5-members.txt"
diff -u "$WORK/expected-md5-members.txt" "$WORK/actual-md5-members.txt"

while read -r expected name; do
  actual="$(md5sum "$CANDIDATE/$name" | cut -d' ' -f1)"
  [ "$expected" = "$actual" ] || {
    printf 'MISMATCH %s expected=%s actual=%s\n' "$name" "$expected" "$actual"
    exit 1
  }
done < "$CANDIDATE/cpio_item_md5"

while IFS= read -r name; do
  case "$name" in
    rootfs|rootfs_sdnand|cpio_item_md5) continue ;;
  esac
  cmp -s "$CANDIDATE/$name" "$STOCK_TREE/$name" || {
    echo "Unexpected non-rootfs change: $name"
    exit 1
  }
done < "$WORK/stock-members.txt"

for image in rootfs rootfs_sdnand; do
  listing="$WORK/$image.list"
  stock_listing="$WORK/$image.stock.list"
  unsquashfs -lln -UTC "$CANDIDATE/$image" > "$listing"
  unsquashfs -lln -UTC "$STOCK_TREE/$image" > "$stock_listing"

  if grep -Ev '^[dlcbps-][rwxStTs-]{9} 0/0 ' "$listing"; then
    echo "$image contains objects not owned by 0:0"
    exit 1
  fi

  grep -Eq '^crw------- 0/0 .* 5[, :] *1 .*squashfs-root/dev/console$' "$listing" \
    || { echo "$image is missing exact /dev/console 5:1 mode 0600"; exit 1; }
  grep -Eq '^-rwxr-xr-x 0/0 .*squashfs-root/etc/init.d/adbd$' "$listing" \
    || { echo "$image has incorrect adbd metadata"; exit 1; }
  grep -Eq '^-rwxrwxr-x 0/0 .*squashfs-root/etc/init.d/S98debug_wifi$' "$listing" \
    || { echo "$image has incorrect S98debug_wifi metadata"; exit 1; }

  grep -vE '(squashfs-root/etc/init.d$|/etc/init.d/(adbd|S98debug_wifi)$)' "$listing" > "$WORK/$image.filtered.list"
  grep -vE '(squashfs-root/etc/init.d$|/etc/init.d/adbd$)' "$stock_listing" > "$WORK/$image.stock.filtered.list"
  diff -u "$WORK/$image.stock.filtered.list" "$WORK/$image.filtered.list"

  while IFS= read -r rel; do
    cmp \
      <(unsquashfs -cat "$CANDIDATE/$image" "$rel") \
      <(unsquashfs -cat "$STOCK_TREE/$image" "$rel") \
      || { echo "$image has unapproved content change: $rel"; exit 1; }
  done < <(
    awk '$1 ~ /^-/ {print $NF}' "$stock_listing" \
      | grep -v '/etc/init.d/adbd$' \
      | sed 's#^squashfs-root/##'
  )

  adbd_hash="$(unsquashfs -cat "$CANDIDATE/$image" etc/init.d/adbd | sha256sum | cut -d' ' -f1)"
  s98_hash="$(unsquashfs -cat "$CANDIDATE/$image" etc/init.d/S98debug_wifi | sha256sum | cut -d' ' -f1)"
  [ "$adbd_hash" = "$EXPECTED_ADBD_SHA256" ] || { echo "$image adbd hash mismatch"; exit 1; }
  [ "$s98_hash" = "$EXPECTED_S98_SHA256" ] || { echo "$image S98debug_wifi hash mismatch"; exit 1; }
done

unsquashfs -lln -UTC "$CANDIDATE/rootfs" \
  | grep -E 'squashfs-root$|/dev/console|/etc/init.d/(adbd|S98debug_wifi)$'

unsquashfs -lln -UTC "$CANDIDATE/rootfs_sdnand" \
  | grep -E 'squashfs-root$|/dev/console|/etc/init.d/(adbd|S98debug_wifi)$'
```

Expected after correction:

```text
0/0 ownership
crw------- 0/0 5,1 squashfs-root/dev/console
executable adbd and S98debug_wifi
```

### 9.2 First boot over USB or hardware recovery console

Before Wi-Fi testing, prove the selected rootfs and effective `/etc`:

```bash
adb devices -l
adb shell 'echo shell-ok'
adb shell 'grep "^Uid:" /proc/self/status; grep "^Gid:" /proc/self/status'
adb shell 'uname -a; cat /proc/cmdline; cat /sys/kernel/aglink_mode'
adb shell 'mount | grep " on /etc "'
adb shell 'ls -ln /etc/init.d/adbd /etc/init.d/S98debug_wifi /etc/etc_complete /etc/etc_need_update 2>/dev/null'
```

Confirm the port variable reached `adbd`:

```bash
adb shell '
P="$(pidof adbd)"
echo "adbd_pid=$P"
tr "\000" "\n" < "/proc/$P/environ" | grep "^ADB_TRANSPORT_PORT="
netstat -ltn
'
```

Expected:

```text
ADB_TRANSPORT_PORT=5555
a listener on TCP 5555
```

### 9.3 Read-only WLAN baseline

```bash
adb shell '
echo "mode=$(cat /sys/kernel/aglink_mode)"
echo "kernel=$(uname -r)"
lsmod
ifconfig -a
route -n
echo "wifi_daemon=$(pidof wifi_daemon)"
ls -l /tmp/UNIX_WIFI.domain 2>/dev/null
'
```

Do not send mode-control commands or start WLAN manually until recovery is proven.

### 9.4 Controlled manual WLAN proof

Only from a recovery-capable USB/console session and while no media, OTA, transfer, or livestream operation is active:

```bash
adb shell '
K="$(uname -r)"
M="/lib/modules/$K/v821_smac.ko"
[ -f "$M" ] || M="/mnt/UDISK/data/lib/modules/$K/v821_smac.ko"
echo "module=$M"
[ -f "$M" ] || exit 1
lsmod | grep -q "^v821_smac " || insmod "$M"
sleep 2
lsmod
ifconfig -a
'
```

Start the daemon and prove its socket:

```bash
adb shell '
if ! pidof wifi_daemon >/dev/null; then
    wifi_daemon </dev/null >/tmp/wifi_daemon.log 2>&1 &
fi
sleep 3
pidof wifi_daemon
ls -l /tmp/UNIX_WIFI.domain
'
```

Open STA and connect:

```bash
adb shell '
wifi -o sta
sleep 3
wifi -c HeyCyanDebug debug1234
sleep 10
wifi -l all
ifconfig wlan0
route -n
cat /etc/resolv.conf
'
```

If it fails, collect:

```bash
adb shell 'cat /tmp/wifi_daemon.log; dmesg; lsmod; ifconfig -a; wifi -i; wifi -l all'
```

### 9.5 TCP ADB and persistence

After WLAN succeeds, put the PC directly on the same isolated 2.4 GHz hotspot. Do not rely on USB tethering to route between USB and hotspot clients.

```bash
adb connect <glasses-ip>:5555
adb devices -l
adb -s <glasses-ip>:5555 shell 'grep "^Uid:" /proc/self/status; uname -a; cat /proc/cmdline'
adb -s <glasses-ip>:5555 shell 'ifconfig -a; route -n; netstat -ltn'
```

Record authentication behavior, UID, bind address, and whether USB/TCP coexist. Then test cold boot, warm reboot, hotspot absent, hotspot present, BLE reconnect, photo, video, audio, media P2P, OTA-mode entry without flashing, stock reflash, and confirmation that TCP 5555 disappears after stock recovery.

## 10. Hardware-Team Questions

1. What is the authoritative cold-boot WLAN sequence for WIFIAM01G1: exact module, module arguments, daemon command, readiness signal, STA-open command, association command, and DHCP completion signal?
2. Is `rtc_init.sh` the only supported loader for `v821_smac.ko`?
3. Is `/etc/rc.d/S97wifi_start` intentionally unused, or is a boot component missing from the captured rootfs?
4. Does `wifi_daemon` daemonize, and is `/tmp/UNIX_WIFI.domain` the correct readiness condition?
5. Can the CLI daemon coexist with `ai_glass_download`, `ai_glass_ota`, and `ai_glass_livestream`, which initialize Wi-Fi libraries directly?
6. What is the safe AP/P2P-to-STA transition and the matching return-to-normal procedure?
7. What is the supported TCP ADB launch form for this fake procd implementation?
8. What address does `adbd` bind, and can it listen before `wlan0` exists?
9. What are the intended ADB authentication/key settings, and is `/mnt/UDISK/adb_keys` required?
10. Does `adbd` retain UID 0 or drop privileges on this production build?
11. How should persistent `/etc` be migrated after NOR and SDNAND rootfs updates?
12. Why does NOR postinstall invalidate SD boot records and clear NOR rootfs data?
13. Why can SDNAND first boot erase SD boot0, and how is `erase_sd_boot0` initialized?
14. Which SWUpdate selector does `ai_glass_ota` choose on the target device: `stable,nor` or `stable,sdnand`?
15. What are the authoritative recovery transports for damaged rootfs, kernel, RISC-V image, boot0, and interrupted postinstall?
16. Is there a tested FEL/EFEX/USB/ISP recovery mode? Provide button/pad sequence, USB VID/PID, host tool, and image format.
17. Must recovery restore the complete image set or can it safely rewrite rootfs only?
18. Can the team provide a known-good SWU build recipe that preserves UID/GID, device nodes, timestamps, compression settings, CPIO metadata, and archive ordering?
19. Does any vendor uploader parse `cpio_item_md5`, and must its format exactly match stock?
20. Is `/dev/console` required in SquashFS before `/files/init` mounts devtmpfs?

## 11. Final Recommendation

Do not send the current SWU to hardware for flashing. Send the blocker list and questions above first. A corrected image should be considered only after:

1. The WLAN and ADB launch paths are corrected.
2. Both rootfs variants are rebuilt metadata-faithfully.
3. Persistent `/etc` behavior is intentionally handled.
4. The exact NOR/SDNAND selector is known.
5. A recovery path has been exercised on the same lab unit.
6. Static checks and first-boot USB/console validation pass before network ADB is attempted.
