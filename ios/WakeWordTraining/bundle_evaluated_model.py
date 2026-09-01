#!/usr/bin/env python3
"""Bundle an evaluated production Jarvis classifier and calibrated threshold."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


MIN_RECALL = 0.90
MAX_FALSE_POSITIVES_PER_HOUR = 0.10
# jarvis.yaml uses 5,000 validation originals and 3 augmentation rounds. LiveKit
# extracts features from every augmented round, so a complete evaluation should
# contain at least 15,000 held-out positive feature examples.
MIN_POSITIVE_EVALUATION_SAMPLES = 15_000
# The phrase-specific negative validation clips alone are below this duration.
# Requiring >=15h ensures the shared ACAV100M validation features were included
# instead of accepting an evaluation based only on synthetic/local negatives.
MIN_VALIDATION_HOURS = 15.0


def parse_args() -> argparse.Namespace:
    training_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        type=Path,
        default=training_dir / "output/jarvis/jarvis.onnx",
    )
    parser.add_argument(
        "--metrics",
        type=Path,
        default=training_dir / "output/jarvis/jarvis_eval.json",
    )
    parser.add_argument(
        "--resources",
        type=Path,
        default=training_dir.parent / "ADGlasses/Resources/WakeWords",
    )
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    args = parse_args()
    if not args.model.is_file() or args.model.stat().st_size == 0:
        raise SystemExit(f"Classifier is missing or empty: {args.model}")
    if not args.metrics.is_file():
        raise SystemExit(f"Evaluation metrics are missing: {args.metrics}")

    try:
        metrics = json.loads(args.metrics.read_text(encoding="utf-8"))
        threshold = float(metrics["optimal_threshold"])
        recall = float(metrics["optimal_recall"])
        false_positives_per_hour = float(metrics["optimal_fpph"])
        positive_samples = int(metrics["n_positive"])
        validation_hours = float(metrics["validation_hours"])
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"Evaluation metrics are incomplete or invalid: {error}") from error

    failures: list[str] = []
    if not 0.0 < threshold < 1.0:
        failures.append(f"invalid optimal threshold {threshold}")
    if recall < MIN_RECALL:
        failures.append(f"recall {recall:.3f} is below {MIN_RECALL:.2f}")
    if false_positives_per_hour > MAX_FALSE_POSITIVES_PER_HOUR:
        failures.append(
            f"false positives/hour {false_positives_per_hour:.3f} exceeds "
            f"{MAX_FALSE_POSITIVES_PER_HOUR:.2f}"
        )
    if positive_samples < MIN_POSITIVE_EVALUATION_SAMPLES:
        failures.append(
            f"only {positive_samples} held-out positive feature examples; expected at least "
            f"{MIN_POSITIVE_EVALUATION_SAMPLES} from 5,000 originals × 3 augmentation rounds"
        )
    if validation_hours < MIN_VALIDATION_HOURS:
        failures.append(
            f"only {validation_hours:.2f} validation hours; expected at least "
            f"{MIN_VALIDATION_HOURS:.1f}h so the shared general-negative validation set is included"
        )
    if failures:
        raise SystemExit("Refusing to bundle an incomplete/underperforming classifier:\n- " + "\n- ".join(failures))

    args.resources.mkdir(parents=True, exist_ok=True)
    destination = args.resources / "jarvis.onnx"
    shutil.copy2(args.model, destination)
    model_hash = sha256(destination)

    # Remove the legacy classifier if it is still present so production builds
    # cannot accidentally package both identities.
    legacy_model = args.resources / "hey_a_d.onnx"
    if legacy_model.exists():
        legacy_model.unlink()

    manifest = {
        "schemaVersion": 1,
        "phrase": "Jarvis",
        "spokenAs": "JAR-vis",
        "modelFile": destination.name,
        "modelName": "jarvis",
        "threshold": threshold,
        "debounceSeconds": 2.0,
        "modelSHA256": model_hash,
        "evaluation": {
            "aut": metrics.get("aut"),
            "recall": recall,
            "falsePositivesPerHour": false_positives_per_hour,
            "positiveSamples": positive_samples,
            "negativeSamples": metrics.get("n_negative"),
            "validationHours": validation_hours,
        },
    }
    (args.resources / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Bundled {destination} ({destination.stat().st_size} bytes)")
    print("Wake phrase: Jarvis; model: jarvis")
    print(
        f"Threshold: {threshold:.4f}; recall: {recall:.3f}; "
        f"FPPH: {false_positives_per_hour:.3f}; validation: {validation_hours:.2f}h"
    )
    print(f"SHA-256: {model_hash}")


if __name__ == "__main__":
    main()
