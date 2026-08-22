#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import os
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)

HARNESS = {
    "tools/full_ad_glasses_one_shot.py",
    ".github/workflows/full-ad-glasses-one-shot.yml",
}


def tracked() -> list[str]:
    out = subprocess.check_output(["git", "ls-files", "-z"])
    return [p.decode("utf-8", "surrogateescape") for p in out.split(b"\0") if p]


def git_rm(path: str) -> None:
    subprocess.run(["git", "rm", "-f", "--ignore-unmatch", "--", path], check=True)


def is_text(path: Path) -> bool:
    try:
        data = path.read_bytes()
    except FileNotFoundError:
        return False
    if b"\0" in data[:8192]:
        return False
    try:
        data.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


# Remove stale cleanup harness/report artifacts from the previous architecture repair.
for stale in [
    "cleanup-build.log",
    ".github/workflows/post-cleanup-repair.yml",
    "tools/post_cleanup_repair.py",
]:
    git_rm(stale)

# Remove obsolete shared-provider tests that exist only to exercise an already-removed
# provider enum. Android preference migration tests are retained below with neutral
# legacy fixtures so fallback behavior is still covered.
for rel in list(tracked()):
    if Path(rel).name not in {"ProviderMigrationTest.kt", "ProviderPersistenceTest.kt"}:
        continue
    path = ROOT / rel
    if path.exists() and is_text(path) and re.search(r"tasker", path.read_text(encoding="utf-8"), flags=re.I):
        git_rm(rel)

# Remove any tracked file or folder whose path itself is Tasker-specific.
for path in sorted(tracked(), key=lambda p: (p.count("/"), len(p)), reverse=True):
    if re.search(r"tasker", path, flags=re.I):
        git_rm(path)

# Rename all tracked legacy brand paths, deepest first. Collapse the old Java/Kotlin
# package path as a unit so com/fersaiyan/cyanbridge becomes exactly com/ad_glasses.
def renamed_path(path: str) -> str:
    new = re.sub(r"com/fersaiyan/cyanbridge", "com/ad_glasses", path, flags=re.I)
    new = re.sub(r"cyanbridge", "AD-Glasses", new, flags=re.I)
    new = re.sub(r"fersaiyan", "ad_glasses", new, flags=re.I)
    return new


for old in sorted(tracked(), key=lambda p: (p.count("/"), len(p)), reverse=True):
    new = renamed_path(old)
    if new != old and Path(old).exists():
        Path(new).parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "mv", "-f", "--", old, new], check=True)

# Text replacements: exact technical identifiers first.
exact_replacements = [
    ("com.fersaiyan.cyanbridge", "com.ad_glasses"),
    ("com/fersaiyan/cyanbridge", "com/ad_glasses"),
    ("com.fersaiyan", "com.ad_glasses"),
    ("com/fersaiyan", "com/ad_glasses"),
    ("android/CyanBridge", "android/AD-Glasses"),
    ("CyanBridge/", "AD-Glasses/"),
    ("/CyanBridge", "/AD-Glasses"),
    ("CyanBridge_", "AD-Glasses_"),
    ("cyanbridge://", "ad-glasses://"),
    ('android:scheme="cyanbridge"', 'android:scheme="ad-glasses"'),
    ("CyanBridgeManagerApp", "AD-Glasses"),
    # Keep plugin publishing/community links, but make the field vendor-neutral.
    ("taskerNetLink", "externalSourceLink"),
    ("TaskerNetLink", "ExternalSourceLink"),
    ("community_source_tasker", "community_source_external"),
    ("plugins_open_tasker", "plugins_open_external"),
    ("publish_tasker_label", "publish_external_source_label"),
    ("publish_taskernet_link", "publish_external_source_link"),
    ("publish_taskernet_hint", "publish_external_source_hint"),
    ("rendersServerTaskerPluginAndRoutesItsInstallAction", "rendersServerExternalPluginAndRoutesItsInstallAction"),
    ("taskerActions", "externalActions"),
    ("retiredTaskerProviderMigratesToCloud", "retiredProviderValueMigratesToCloud"),
    ("oldTaskerProviderMigratesToNativeLocalProvider", "oldProviderValueMigratesToNativeLocalProvider"),
    ('"TASKER"', '"LEGACY_PROVIDER"'),
    ("Open in Tasker", "Open external source"),
    ("TaskerNet link *", "External source link *"),
    ("Enter the TaskerNet URL for your profile.", "Enter the external source URL for your plugin."),
    ("https://tasker.dev", "https://example.com/external-source"),
    ("https://taskernet.com/...", "https://example.com/external-source"),
    ("https://taskernet.com", "https://example.com/external-source"),
]

code_exts = {".kt", ".java", ".gradle", ".kts", ".aidl", ".groovy"}

for rel in tracked():
    if rel in HARNESS:
        continue
    path = ROOT / rel
    if not path.exists() or not path.is_file() or not is_text(path):
        continue
    text = path.read_text(encoding="utf-8")
    new = text
    for old, repl in exact_replacements:
        new = new.replace(old, repl)

    # Catch every remaining brand casing variant without making source identifiers invalid.
    if path.suffix.lower() in code_exts:
        new = re.sub(r"cyanbridge", "AD_GLASSES", new, flags=re.I)
        new = re.sub(r"fersaiyan", "ad_glasses", new, flags=re.I)
    else:
        new = re.sub(r"cyanbridge", "AD Glasses", new, flags=re.I)
        new = re.sub(r"fersaiyan", "AD Glasses", new, flags=re.I)
        # Remaining prose labels become vendor-neutral. Known XML resource identifiers
        # are renamed above before this prose pass, so their names remain valid.
        new = re.sub(r"tasker", "external automation", new, flags=re.I)

    # Canonical technical spellings after the broad prose pass.
    new = new.replace("ad_glasses://", "ad-glasses://")
    new = new.replace("AD Glasses://", "ad-glasses://")
    new = new.replace('android:scheme="AD Glasses"', 'android:scheme="ad-glasses"')
    new = new.replace('rootProject.name = "AD GlassesManagerApp"', 'rootProject.name = "AD-Glasses"')
    new = new.replace('rootProject.name = "AD Glasses"', 'rootProject.name = "AD-Glasses"')
    new = new.replace("android/AD Glasses", "android/AD-Glasses")
    new = new.replace("AD Glasses/", "AD-Glasses/")

    if new != text:
        path.write_text(new, encoding="utf-8")

architecture_note = """
## Current AD Glasses AI architecture

The Android app is owned under `com.ad_glasses` and the Android project lives at
`android/AD-Glasses`.

The supported assistant stack is intentionally limited to:
- Cloud REST requests for conventional cloud inference.
- Cloud Realtime / Gemini Live API for low-latency conversational sessions.
- Local LLM fallback for offline/on-device inference when cloud execution is unavailable or undesired.
- Android TTS for speech output.
- The AD default-assistant implementation for Android assistant-role integration.

The canonical deep-link scheme is `ad-glasses://`.
""".strip() + "\n"

for rel in [
    "README.md",
    "android/AD-Glasses/AD_ASSISTANT_RUNTIME.md",
    "android/AD-Glasses/README_LOCAL_AGENT.md",
]:
    path = ROOT / rel
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    marker = "## Current AD Glasses AI architecture"
    if marker in text:
        text = text.split(marker, 1)[0].rstrip() + "\n\n"
    path.write_text(text.rstrip() + "\n\n" + architecture_note, encoding="utf-8")

bad_paths = [p for p in tracked() if re.search(r"cyanbridge|fersaiyan|tasker", p, flags=re.I)]
if bad_paths:
    print("Forbidden legacy terms remain in tracked paths:", file=sys.stderr)
    for p in bad_paths:
        print(p, file=sys.stderr)
    sys.exit(20)

bad_text = []
for rel in tracked():
    if rel in HARNESS:
        continue
    path = ROOT / rel
    if not path.exists() or not path.is_file() or not is_text(path):
        continue
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if re.search(r"cyanbridge|fersaiyan|tasker", line, flags=re.I):
            bad_text.append(f"{rel}:{i}:{line}")
if bad_text:
    print("Forbidden legacy terms remain in tracked text:", file=sys.stderr)
    print("\n".join(bad_text[:500]), file=sys.stderr)
    sys.exit(21)

for rel in ["android/AD-Glasses/app", "android/AD-Glasses/shared"]:
    if not (ROOT / rel).exists():
        print(f"Missing required renamed project path: {rel}", file=sys.stderr)
        sys.exit(22)

print("One-shot AD Glasses transformation applied.")
subprocess.run(["git", "status", "--short"], check=True)
