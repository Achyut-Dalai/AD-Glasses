#!/usr/bin/env python3
"""Bundle an evaluated production Hey A D classifier and its calibrated threshold."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


MIN_RECALL = 0.90
MAX_FALSE_POSITIVES_PER_HOUR = 0.10
MIN_POSITIVE_EVALUATION_SAMPLES = 5_000


def parse_args() -> argparse.Namespace:
    training_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        type=Path,
        default=training_dir / "output/hey_a_d/hey_a_d.onnx",
    )
    parser.add_argument(
        "--metrics",
        type=Path,
        default=training_dir / "output/hey_a_d/hey_a_d_eval.json",
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

    metrics = json.loads(args.metrics.read_text(encoding="utf-8"))
    threshold = float(metrics["optimal_threshold"])
    recall = float(metrics["optimal_recall"])
    false_positives_per_hour = float(metrics["optimal_fpph"])
    positive_samples = int(metrics["n_positive"])

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
            f"only {positive_samples} held-out positives; expected at least "
            f"{MIN_POSITIVE_EVALUATION_SAMPLES}"
        )
    if failures:
        raise SystemExit("Refusing to bundle an unevaluated classifier:\n- " + "\n- ".join(failures))

    args.resources.mkdir(parents=True, exist_ok=True)
    destination = args.resources / "hey_a_d.onnx"
    shutil.copy2(args.model, destination)
    model_hash = sha256(destination)

    manifest = {
        "schemaVersion": 1,
        "phrase": "Hey A D",
        "spokenAs": "hey A-dee",
        "modelFile": destination.name,
        "modelName": "hey_a_d",
        "threshold": threshold,
        "debounceSeconds": 2.0,
        "modelSHA256": model_hash,
        "evaluation": {
            "aut": metrics.get("aut"),
            "recall": recall,
            "falsePositivesPerHour": false_positives_per_hour,
            "positiveSamples": positive_samples,
            "negativeSamples": metrics.get("n_negative"),
            "validationHours": metrics.get("validation_hours"),
        },
    }
    (args.resources / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Bundled {destination} ({destination.stat().st_size} bytes)")
    print(f"Threshold: {threshold:.4f}; SHA-256: {model_hash}")


if __name__ == "__main__":
    main()
