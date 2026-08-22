#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import os
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)


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

# Remove any tracked file or folder whose path itself is Tasker-specific.
for path in sorted(tracked(), key=lambda p: (p.count("/"), len(p)), reverse=True):
    if re.search(r"tasker", path, flags=re.I):
        git_rm(path)

# Rename all tracked legacy brand paths, deepest first.
def renamed_path(path: str) -> str:
    parts = path.split("/")
    out = []
    for part in parts:
        part = part.replace("CyanBridge", "AD-Glasses")
        part = part.replace("cyanbridge", "ad_glasses")
        part = part.replace("Fersaiyan", "AD-Glasses")
        part = part.replace("fersaiyan", "ad_glasses")
        out.append(part)
    return "/".join(out)


for old in sorted(tracked(), key=lambda p: (p.count("/"), len(p)), reverse=True):
    new = renamed_path(old)
    if new != old and Path(old).exists():
        Path(new).parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "mv", "-f", "--", old, new], check=True)

# Text replacements: exact technical identifiers first, then remaining brand prose.
replacements = [
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
    ("CyanBridge", "AD Glasses"),
    ("cyanbridge", "ad_glasses"),
    ("fersaiyan", "ad_glasses"),
]

for rel in tracked():
    path = ROOT / rel
    if not path.exists() or not path.is_file() or not is_text(path):
        continue
    text = path.read_text(encoding="utf-8")
    new = text
    for old, repl in replacements:
        new = new.replace(old, repl)
    new = new.replace("ad_glasses://", "ad-glasses://")
    new = new.replace('rootProject.name = "AD GlassesManagerApp"', 'rootProject.name = "AD-Glasses"')
    new = new.replace('rootProject.name = "AD Glasses"', 'rootProject.name = "AD-Glasses"')
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

# Remove Tasker prose lines from documentation/config only; code references are audited and fail below.
doc_exts = {".md", ".txt", ".yml", ".yaml", ".json", ".xml", ".properties"}
for rel in tracked():
    path = ROOT / rel
    if not path.exists() or not path.is_file() or path.suffix.lower() not in doc_exts or not is_text(path):
        continue
    text = path.read_text(encoding="utf-8")
    if re.search(r"tasker", text, flags=re.I):
        kept = [line for line in text.splitlines() if not re.search(r"tasker", line, flags=re.I)]
        path.write_text("\n".join(kept).rstrip() + "\n", encoding="utf-8")

bad_paths = [p for p in tracked() if re.search(r"cyanbridge|fersaiyan|tasker", p, flags=re.I)]
if bad_paths:
    print("Forbidden legacy terms remain in tracked paths:", file=sys.stderr)
    for p in bad_paths:
        print(p, file=sys.stderr)
    sys.exit(20)

bad_text = []
for rel in tracked():
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
