#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COREAI_ROOT="$ROOT/ios/.coreai"
CHECKOUT="$COREAI_ROOT/coreai-models"
EXPORTS="$COREAI_ROOT/exports"
PIN="cefd53d70a453518861d1958cdbb4dddab8ece34"
MODEL_NAME="ADQwen3_0_6B_iOS"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required. Install it with: brew install uv" >&2
  exit 1
fi

mkdir -p "$COREAI_ROOT" "$EXPORTS"

if [[ ! -d "$CHECKOUT/.git" ]]; then
  git clone https://github.com/apple/coreai-models.git "$CHECKOUT"
fi

git -C "$CHECKOUT" fetch --tags origin
git -C "$CHECKOUT" checkout --detach "$PIN"

cd "$CHECKOUT"
echo "Exporting Qwen3 0.6B for iOS with a 2048-token static context..."
uv run coreai.llm.export qwen3-0.6b \
  --platform iOS \
  --max-context-length 2048 \
  --output-dir "$EXPORTS" \
  --output-name "$MODEL_NAME" \
  --overwrite

echo
echo "Core AI export complete."
echo "Expected model bundle: $EXPORTS/$MODEL_NAME"
echo "Next: ios/scripts/stage-qwen3-coreai-device.sh <DEVICE_ID>"
