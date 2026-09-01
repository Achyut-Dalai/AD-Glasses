#!/usr/bin/env python3
"""Guarded production training runner for the Jarvis wake-word classifier.

This wrapper intentionally stops after LiveKit's three-phase training. Export,
evaluation, and app bundling remain explicit post-training steps.

Why this exists:
- the pinned LiveKit revision adds a SessionOptions argument to feature
  extraction without updating the CLI augment/run call sites;
- Piper generation can skip a failed batch while advancing clip indices,
  leaving holes that the upstream count-based resume logic cannot repair.

The wrapper keeps the exact pinned LiveKit revision, repairs/validates generated
splits, invokes feature extraction compatibly, verifies all expected feature
shapes, and only then starts the expensive 120k-step production training.
"""

from __future__ import annotations

import argparse
import importlib.metadata
import inspect
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


PINNED_LIVEKIT_COMMIT = "95448a7559c453fcd87645bd67b247ffb45f85b0"
CONFIG_NAME = "jarvis.yaml"
MODEL_NAME = "jarvis"
DEFAULT_MIN_FREE_GIB = 20.0
MAX_GENERATION_ATTEMPTS = 5
ORIGINAL_CLIP_RE = re.compile(r"^clip_(\d{6})\.wav$")
AUGMENTED_CLIP_RE = re.compile(r"^clip_(\d{6})_r(\d+)\.wav$")


def fail(message: str) -> "NoReturn":
    raise SystemExit(f"ERROR: {message}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Preflight, generate, augment, extract, validate, and train Jarvis safely."
    )
    parser.add_argument(
        "--preflight-only",
        action="store_true",
        help="Validate the environment/assets/config and exit before generating data.",
    )
    parser.add_argument(
        "--fresh",
        action="store_true",
        help="Delete output/jarvis before starting. Use for the first production Jarvis run.",
    )
    parser.add_argument(
        "--min-free-gib",
        type=float,
        default=DEFAULT_MIN_FREE_GIB,
        help=f"Minimum free disk required before generation (default: {DEFAULT_MIN_FREE_GIB:g} GiB).",
    )
    return parser.parse_args()


def training_dir() -> Path:
    return Path(__file__).resolve().parent


def verify_python() -> None:
    if sys.version_info < (3, 11):
        fail(f"Python 3.11+ is required; found {sys.version.split()[0]}")


def installed_livekit_commit() -> str | None:
    try:
        dist = importlib.metadata.distribution("livekit-wakeword")
    except importlib.metadata.PackageNotFoundError:
        fail("livekit-wakeword is not installed in this Python environment")

    raw = dist.read_text("direct_url.json")
    if not raw:
        return None
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return None
    vcs = data.get("vcs_info") or {}
    return vcs.get("commit_id") or vcs.get("requested_revision")


def verify_livekit_pin() -> None:
    commit = installed_livekit_commit()
    if not commit:
        fail(
            "Could not verify the installed livekit-wakeword Git revision. Reinstall the pinned "
            f"dependency at {PINNED_LIVEKIT_COMMIT}."
        )
    if not PINNED_LIVEKIT_COMMIT.startswith(commit) and not commit.startswith(PINNED_LIVEKIT_COMMIT):
        fail(
            "Installed livekit-wakeword revision does not match this project. "
            f"Expected {PINNED_LIVEKIT_COMMIT}, found {commit}."
        )
    print(f"LiveKit pin: {PINNED_LIVEKIT_COMMIT}")


def require_command(name: str) -> str:
    path = shutil.which(name)
    if not path:
        fail(f"Required command is missing: {name}")
    return path


def require_file(path: Path, description: str, *, min_bytes: int = 1) -> None:
    if not path.is_file():
        fail(f"Missing {description}: {path}")
    if path.stat().st_size < min_bytes:
        fail(f"{description} is unexpectedly small/empty: {path}")


def require_wavs(path: Path, description: str) -> None:
    if not path.is_dir():
        fail(f"Missing {description} directory: {path}")
    if not next(path.rglob("*.wav"), None):
        fail(f"No WAV files found in {description}: {path}")


def free_gib(path: Path) -> float:
    return shutil.disk_usage(path).free / (1024**3)


def print_pronunciation() -> None:
    espeak = require_command("espeak-ng")
    result = subprocess.run(
        [espeak, "--ipa", "-q", "-v", "en-us", "jarvis"],
        capture_output=True,
        text=True,
        check=True,
    )
    ipa = result.stdout.strip()
    if not ipa:
        fail("espeak-ng returned no IPA for 'jarvis'")
    print(f"Jarvis IPA (espeak-ng): {ipa}")


def load_production_config(config_path: Path):
    from livekit.wakeword.config import load_config

    config = load_config(config_path)
    if config.model_name != MODEL_NAME:
        fail(f"Expected model_name '{MODEL_NAME}', found '{config.model_name}'")
    normalized_targets = [p.strip().lower() for p in config.target_phrases]
    if normalized_targets != ["jarvis"]:
        fail(f"Production target_phrases must be exactly ['jarvis']; found {config.target_phrases}")
    if config.n_samples != 25_000 or config.n_samples_val != 5_000:
        fail("Production Jarvis config must remain at 25,000 train / 5,000 validation samples")
    if config.n_background_samples != 2_000 or config.n_background_samples_val != 500:
        fail("Production Jarvis background samples must remain at 2,000 train / 500 validation")
    if config.augmentation.rounds != 3:
        fail("Production Jarvis config must use exactly 3 augmentation rounds")
    if config.steps != 100_000:
        fail("Production Jarvis config must use 100,000 phase-1 steps")
    if abs(float(config.target_fp_per_hour) - 0.1) > 1e-9:
        fail("Production Jarvis target_fp_per_hour must remain 0.1")
    if config.model.model_type.value != "conv_attention" or config.model.model_size.value != "medium":
        fail("Production Jarvis model must remain medium conv_attention")
    if config.tts_batch_size > 25:
        fail("tts_batch_size is above the guarded project limit of 25")
    if any("jarvis" in phrase.lower() for phrase in config.custom_negative_phrases):
        fail("custom_negative_phrases must not contain the target word 'jarvis'")
    return config


def preflight(config, root: Path, min_free_gib: float) -> None:
    verify_python()
    verify_livekit_pin()
    require_command("ffmpeg")
    require_command("espeak-ng")
    print_pronunciation()

    require_file(config.piper_checkpoint_path, "Piper VITS checkpoint", min_bytes=1024 * 1024)
    require_file(config.piper_checkpoint_path.with_suffix(".json"), "Piper VITS config JSON")

    features_dir = config.data_path / "features"
    require_file(
        features_dir / "openwakeword_features_ACAV100M_2000_hrs_16bit.npy",
        "ACAV100M general-negative feature set",
        min_bytes=1024 * 1024,
    )
    require_file(
        features_dir / "validation_set_features.npy",
        "ACAV100M validation feature set",
        min_bytes=1024 * 1024,
    )

    for raw in config.augmentation.background_paths:
        require_wavs(Path(raw), "background-noise")
    for raw in config.augmentation.rir_paths:
        require_wavs(Path(raw), "room-impulse-response")

    available = free_gib(root)
    print(f"Free disk: {available:.1f} GiB")
    if available < min_free_gib:
        fail(
            f"Only {available:.1f} GiB free; require at least {min_free_gib:.1f} GiB for the "
            "production generation/augmentation/feature pipeline."
        )

    # mmap checks validate that the large NPY headers/shapes are readable without loading them.
    import numpy as np

    acav = np.load(
        features_dir / "openwakeword_features_ACAV100M_2000_hrs_16bit.npy",
        mmap_mode="r",
    )
    validation = np.load(features_dir / "validation_set_features.npy", mmap_mode="r")
    if acav.size == 0 or validation.size == 0:
        fail("One or more shared negative feature arrays are empty")
    print(f"ACAV100M features: shape={acav.shape}, dtype={acav.dtype}")
    print(f"Validation features: shape={validation.shape}, dtype={validation.dtype}")
    print("Preflight: PASS")


def split_expectations(config) -> dict[str, int]:
    return {
        "positive_train": int(config.n_samples),
        "positive_test": int(config.n_samples_val),
        "negative_train": int(config.n_samples),
        "negative_test": int(config.n_samples_val),
        "background_train": int(config.n_background_samples),
        "background_test": int(config.n_background_samples_val),
    }


def original_indices(split_dir: Path) -> set[int]:
    indices: set[int] = set()
    if not split_dir.is_dir():
        return indices
    for path in split_dir.glob("clip_*.wav"):
        match = ORIGINAL_CLIP_RE.match(path.name)
        if match:
            indices.add(int(match.group(1)))
    return indices


def first_missing_index(indices: set[int], expected: int) -> int | None:
    for index in range(expected):
        if index not in indices:
            return index
    return None


def normalize_partial_split(split_dir: Path, expected: int) -> None:
    """Make upstream count-based resume safe by truncating after the first hole."""
    if not split_dir.is_dir():
        return
    indices = original_indices(split_dir)
    missing = first_missing_index(indices, expected)

    # Delete any out-of-range originals left by an old/mismatched config.
    for path in split_dir.glob("clip_*.wav"):
        match = ORIGINAL_CLIP_RE.match(path.name)
        if match and int(match.group(1)) >= expected:
            path.unlink()

    if missing is None:
        return
    if not any(index > missing for index in indices):
        return

    print(f"Repairing {split_dir.name}: truncating from first missing clip {missing:06d}")
    for path in split_dir.glob("clip_*.wav"):
        match = ORIGINAL_CLIP_RE.match(path.name)
        if match and int(match.group(1)) >= missing:
            path.unlink()


def validate_original_split(split_dir: Path, expected: int) -> list[str]:
    problems: list[str] = []
    indices = original_indices(split_dir)
    wanted = set(range(expected))
    missing = sorted(wanted - indices)
    extra = sorted(indices - wanted)
    if missing:
        preview = ", ".join(f"{i:06d}" for i in missing[:8])
        problems.append(f"missing {len(missing)} original clips (first: {preview})")
    if extra:
        preview = ", ".join(f"{i:06d}" for i in extra[:8])
        problems.append(f"has {len(extra)} out-of-range clips (first: {preview})")

    for index in indices & wanted:
        path = split_dir / f"clip_{index:06d}.wav"
        if path.stat().st_size <= 44:
            problems.append(f"clip_{index:06d}.wav is empty/corrupt-sized")
            break
    return problems


def generate_complete_dataset(config) -> None:
    from livekit.wakeword.data.generate import run_generate

    expected = split_expectations(config)
    model_dir = config.model_output_dir

    for attempt in range(1, MAX_GENERATION_ATTEMPTS + 1):
        print(f"\n=== Generation attempt {attempt}/{MAX_GENERATION_ATTEMPTS} ===")
        for split, count in expected.items():
            normalize_partial_split(model_dir / split, count)

        generation_error: Exception | None = None
        try:
            run_generate(config)
        except Exception as exc:  # repair partial output, then retry a bounded number of times
            generation_error = exc
            print(f"Generation attempt raised: {exc!r}")

        problems: list[str] = []
        for split, count in expected.items():
            split_problems = validate_original_split(model_dir / split, count)
            if split_problems:
                problems.append(f"{split}: " + "; ".join(split_problems))

        if not problems and generation_error is None:
            print("Generation completeness: PASS")
            return

        if problems:
            print("Generation is incomplete:")
            for problem in problems:
                print(f"  - {problem}")

        if attempt == MAX_GENERATION_ATTEMPTS:
            if generation_error:
                fail(f"Generation failed after {attempt} attempts; last error: {generation_error}")
            fail(f"Generation remained incomplete after {attempt} attempts")

    fail("Unexpected generation loop exit")


def clear_derived_artifacts(config) -> None:
    """Remove stale outputs that could otherwise be mistaken for this run's model."""
    model_dir = config.model_output_dir
    if not model_dir.exists():
        return

    for pattern in (
        "*_features_*.npy",
        "*_metrics.json",
        "*_eval.json",
        "*_det.png",
        "*.pt",
        "*.onnx",
        "*.tflite",
    ):
        for path in model_dir.glob(pattern):
            path.unlink()


def validate_augmented_splits(config) -> None:
    expected = split_expectations(config)
    rounds = int(config.augmentation.rounds)
    model_dir = config.model_output_dir
    problems: list[str] = []

    for split, count in expected.items():
        split_dir = model_dir / split
        by_round: dict[int, set[int]] = {round_idx: set() for round_idx in range(rounds)}
        if split_dir.is_dir():
            for path in split_dir.glob("clip_*_r*.wav"):
                match = AUGMENTED_CLIP_RE.match(path.name)
                if match:
                    index = int(match.group(1))
                    round_idx = int(match.group(2))
                    if round_idx in by_round:
                        by_round[round_idx].add(index)
        wanted = set(range(count))
        for round_idx in range(rounds):
            missing = wanted - by_round[round_idx]
            extra = by_round[round_idx] - wanted
            if missing or extra:
                problems.append(
                    f"{split} r{round_idx}: expected {count}, found {len(by_round[round_idx])} "
                    f"(missing={len(missing)}, extra={len(extra)})"
                )

    if problems:
        fail("Augmentation completeness failed:\n  - " + "\n  - ".join(problems))
    print("Augmentation completeness: PASS")


def run_feature_extraction_compat(config) -> None:
    """Call extraction across both the pre- and post-SessionOptions LiveKit APIs."""
    from livekit.wakeword.data.features import run_extraction

    parameters = inspect.signature(run_extraction).parameters
    if len(parameters) == 1:
        run_extraction(config)
        return
    if len(parameters) == 2:
        from onnxruntime import SessionOptions

        run_extraction(config, SessionOptions())
        return
    fail(f"Unsupported LiveKit run_extraction signature: {inspect.signature(run_extraction)}")


def validate_feature_shapes(config) -> None:
    import numpy as np

    rounds = int(config.augmentation.rounds)
    model_dir = config.model_output_dir
    expected = {
        "positive_features_train.npy": int(config.n_samples) * rounds,
        "positive_features_test.npy": int(config.n_samples_val) * rounds,
        "negative_features_train.npy": int(config.n_samples) * rounds,
        "negative_features_test.npy": int(config.n_samples_val) * rounds,
        "background_noise_features_train.npy": int(config.n_background_samples) * rounds,
        "background_noise_features_test.npy": int(config.n_background_samples_val) * rounds,
    }

    problems: list[str] = []
    for filename, rows in expected.items():
        path = model_dir / filename
        if not path.is_file():
            problems.append(f"missing {filename}")
            continue
        array = np.load(path, mmap_mode="r")
        wanted = (rows, 16, 96)
        if array.shape != wanted:
            problems.append(f"{filename}: expected {wanted}, found {array.shape}")
        else:
            print(f"Feature check: {filename} {array.shape}")

    if problems:
        fail("Feature extraction completeness failed:\n  - " + "\n  - ".join(problems))
    print("Feature extraction completeness: PASS")


def run_training(config) -> None:
    from livekit.wakeword.training.trainer import run_train

    print("\n=== Starting production training: 100k + 10k + 10k optimizer steps ===")
    model_path = Path(run_train(config))
    require_file(model_path, "trained Jarvis PyTorch model", min_bytes=1024)
    print(f"Training complete: {model_path}")
    print("Next: export, eval, then run bundle_evaluated_model.py only if evaluation passes.")


def main() -> None:
    args = parse_args()
    root = training_dir()
    os.chdir(root)
    config_path = root / CONFIG_NAME
    require_file(config_path, "Jarvis production config")

    # Verify pin before importing the training package's implementation modules.
    verify_python()
    verify_livekit_pin()
    config = load_production_config(config_path)

    if args.fresh and config.model_output_dir.exists():
        print(f"Removing existing production output: {config.model_output_dir}")
        shutil.rmtree(config.model_output_dir)

    preflight(config, root, args.min_free_gib)
    if args.preflight_only:
        return

    generate_complete_dataset(config)

    # Everything after originals is regenerated from scratch for reproducibility.
    clear_derived_artifacts(config)

    from livekit.wakeword.data.augment import run_augment

    print("\n=== Augmenting Jarvis clips (3 rounds) ===")
    run_augment(config)
    validate_augmented_splits(config)

    print("\n=== Extracting frozen mel/embedding features ===")
    run_feature_extraction_compat(config)
    validate_feature_shapes(config)

    run_training(config)


if __name__ == "__main__":
    main()
