#!/usr/bin/env python3
"""Static checks for the KMP iOS host that are safe to run without Xcode."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / "ios" / "QCSDKDemo.xcodeproj" / "project.pbxproj"
HOST = ROOT / "ios" / "CyanBridgeKMPHost" / "CyanBridgeKMPHostApp.swift"
DEMO_APP_DELEGATE = ROOT / "ios" / "QCSDKDemo" / "AppDelegate.m"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def block(contents: str, marker: str) -> str:
    start = contents.find(marker)
    require(start >= 0, f"Missing project section: {marker}")
    end = contents.find("\n\t\t};", start)
    require(end >= 0, f"Unterminated project section: {marker}")
    return contents[start:end]


def main() -> int:
    project = PROJECT.read_text(encoding="utf-8")
    host = HOST.read_text(encoding="utf-8")
    demo_app_delegate = DEMO_APP_DELEGATE.read_text(encoding="utf-8")

    require("import CyanBridgeShared" in host, "The KMP host must import CyanBridgeShared.")
    require(
        "meetingSummaryPreviewMarkdown()" in host,
        "The KMP host must render the shared meeting-summary preview.",
    )
    for forbidden in ("QCSDK", "CoreBluetooth", "NetworkExtension"):
        require(forbidden not in host, f"The KMP host must not import vendor transport: {forbidden}")

    host_target = block(project, 'CB2000092F00000100CB0001 /* CyanBridgeKMPHost */ = {')
    require("Build CyanBridgeShared" in host_target, "The KMP host must build the shared framework.")
    host_debug = block(project, 'CB20000A2F00000100CB0001 /* Debug */ = {')
    host_release = block(project, 'CB20000B2F00000100CB0001 /* Release */ = {')
    for configuration in (host_debug, host_release):
        require("CyanBridgeShared" in configuration, "The KMP host must link the shared framework.")
        require("xcode-frameworks" in configuration, "The KMP host must search Gradle framework output.")

    demo_target = block(project, 'AA1313562E2F903500B03938 /* QCSDKDemo */ = {')
    demo_debug = block(project, 'AA1313702E2F903600B03938 /* Debug */ = {')
    demo_release = block(project, 'AA1313712E2F903600B03938 /* Release */ = {')
    for section in (demo_target, demo_debug, demo_release, demo_app_delegate):
        require("CyanBridgeShared" not in section, "QCSDKDemo must not reference CyanBridgeShared.")
    require(
        "CyanBridgeSharedIntegration" not in project,
        "The legacy QCSDKDemo KMP smoke wrapper must not remain in the project.",
    )

    print("KMP iOS host wiring is structurally valid.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError) as error:
        print(f"KMP iOS host verification failed: {error}", file=sys.stderr)
        raise SystemExit(1)
