#!/usr/bin/env python3
"""Check or update AD-Glasses Android toolchain pins against Google's stable channel."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

ANDROID_GRADLE_FILES = [
    ROOT / "android/CyanBridge/app/build.gradle",
    ROOT / "android/CyanBridge/shared/build.gradle.kts",
    ROOT / "android/CyanBridge/assistant-role/build.gradle.kts",
    ROOT / "android/CyanBridge/moonshine-voice/build.gradle",
    ROOT / "heycyan-core/core-connectivity/build.gradle.kts",
    ROOT / "heycyan-core/core-ble/build.gradle.kts",
    ROOT / "heycyan-core/core-audio/build.gradle.kts",
    ROOT / "heycyan-core/core-transcription-api/build.gradle.kts",
    ROOT / "heycyan-core/core-summarization-api/build.gradle.kts",
    ROOT / "heycyan-core/core-data/build.gradle.kts",
    ROOT / "heycyan-core/core-utils/build.gradle.kts",
]


def run_sdkmanager_list() -> str:
    try:
        proc = subprocess.run(
            ["sdkmanager", "--list", "--channel=0"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError) as exc:
        raise RuntimeError(
            "sdkmanager is required. Run scripts/bootstrap-android-toolchain.sh first "
            "or install Android SDK Command-Line Tools."
        ) from exc
    return proc.stdout


def numeric_key(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


def latest_numeric_package(output: str, prefix: str) -> str:
    versions: list[str] = []
    pattern = re.compile(rf"^\s*{re.escape(prefix)}([0-9]+(?:\.[0-9]+)*)\s+\|", re.MULTILINE)
    for match in pattern.finditer(output):
        versions.append(match.group(1))
    if not versions:
        raise RuntimeError(f"No stable {prefix} package was reported by sdkmanager")
    return max(set(versions), key=numeric_key)


def latest_platform(output: str) -> int:
    values = [
        int(match.group(1))
        for match in re.finditer(r"^\s*platforms;android-([0-9]+)\s+\|", output, re.MULTILINE)
    ]
    if not values:
        raise RuntimeError("No stable Android platform package was reported by sdkmanager")
    return max(values)


def latest_cmdline_tools_download_id() -> str:
    request = urllib.request.Request(
        "https://developer.android.com/studio",
        headers={"User-Agent": "AD-Glasses-toolchain-audit/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        html = response.read().decode("utf-8", errors="replace")
    match = re.search(r"commandlinetools-linux-([0-9]+)_latest\.zip", html)
    if not match:
        raise RuntimeError("Could not find the stable Android command-line tools download id")
    return match.group(1)


def replace_checked(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text)
    if count == 0:
        raise RuntimeError(f"Could not locate {label} pin to update")
    return updated


def update_file(path: Path, transforms: list[tuple[str, str, str]]) -> bool:
    original = path.read_text(encoding="utf-8")
    updated = original
    for pattern, replacement, label in transforms:
        updated = replace_checked(updated, pattern, replacement, f"{label} in {path}")
    if updated == original:
        return False
    path.write_text(updated, encoding="utf-8")
    return True


def apply_updates(platform: int, build_tools: str, ndk: str, cmake: str, cmdline_id: str) -> list[Path]:
    changed: list[Path] = []

    workflow = ROOT / ".github/workflows/android-app.yml"
    if update_file(
        workflow,
        [
            (r"cmdline-tools-version:\s*[0-9]+", f"cmdline-tools-version: {cmdline_id}", "command-line tools"),
            (r'"platforms;android-[0-9]+"', f'"platforms;android-{platform}"', "Android platform"),
            (r'"build-tools;[0-9.]+"', f'"build-tools;{build_tools}"', "Build Tools"),
            (r'"cmake;[0-9.]+"', f'"cmake;{cmake}"', "CMake"),
            (r'"ndk;[0-9.]+"', f'"ndk;{ndk}"', "NDK"),
        ],
    ):
        changed.append(workflow)

    for path in ANDROID_GRADLE_FILES:
        transforms: list[tuple[str, str, str]] = [
            (r"compileSdk\s*=\s*[0-9]+", f"compileSdk = {platform}", "compileSdk"),
        ]
        if path.name == "build.gradle" and path.parent.name == "app":
            transforms.extend(
                [
                    (r"targetSdk\s*=\s*[0-9]+", f"targetSdk = {platform}", "targetSdk"),
                    (r'ndkVersion\s*=\s*"[0-9.]+"', f'ndkVersion = "{ndk}"', "NDK"),
                ]
            )
        if path.parent.name == "moonshine-voice":
            transforms.extend(
                [
                    (r"ndkVersion\s*=\s*'[0-9.]+'", f"ndkVersion = '{ndk}'", "Moonshine NDK"),
                    (r"version\s*=\s*'[0-9.]+'", f"version = '{cmake}'", "Moonshine CMake"),
                ]
            )
        if update_file(path, transforms):
            changed.append(path)

    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="rewrite repository pins")
    args = parser.parse_args()

    output = run_sdkmanager_list()
    platform = latest_platform(output)
    build_tools = latest_numeric_package(output, "build-tools;")
    ndk = latest_numeric_package(output, "ndk;")
    cmake = latest_numeric_package(output, "cmake;")
    cmdline_id = latest_cmdline_tools_download_id()

    print(f"Stable Android platform: API {platform}")
    print(f"Stable Build Tools: {build_tools}")
    print(f"Stable NDK: {ndk}")
    print(f"Stable CMake: {cmake}")
    print(f"Stable Command-Line Tools download id: {cmdline_id}")

    if not args.apply:
        return 0

    changed = apply_updates(platform, build_tools, ndk, cmake, cmdline_id)
    if changed:
        print("Updated repository pins:")
        for path in changed:
            print(f"  {path.relative_to(ROOT)}")
    else:
        print("Repository toolchain pins are already current.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as exc:
        print(f"toolchain audit failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
