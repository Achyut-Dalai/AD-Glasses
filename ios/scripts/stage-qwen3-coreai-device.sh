#!/bin/bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <DEVICE_ID> [BUNDLE_ID]" >&2
  exit 2
fi

DEVICE_ID="$1"
BUNDLE_ID="${2:-com.achyutdalai.ADGlasses}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODEL_DIR="$ROOT/ios/.coreai/exports/ADQwen3_0_6B_iOS"
DESTINATION="Documents/CoreAIModels/ADQwen3_0_6B_iOS"

if [[ ! -d "$MODEL_DIR" ]]; then
  echo "Model export not found at: $MODEL_DIR" >&2
  echo "Run ios/scripts/export-qwen3-coreai.sh first." >&2
  exit 1
fi

cat <<EOF
Staging Qwen3 0.6B to AD Glasses.
Device: $DEVICE_ID
Bundle: $BUNDLE_ID
Destination: $DESTINATION

The app must already be installed and launched once on this iPhone.
EOF

xcrun devicectl device copy to \
  --device "$DEVICE_ID" \
  --domain-type appDataContainer \
  --domain-identifier "$BUNDLE_ID" \
  --source "$MODEL_DIR" \
  --destination "$DESTINATION"

echo
echo "Model staged. Force-quit and relaunch AD Glasses to prewarm the local semantic router."
