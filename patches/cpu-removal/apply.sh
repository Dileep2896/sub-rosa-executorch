#!/usr/bin/env bash
#
# Apply the NPU-only (remove-CPU) patch.
#
# ⚠️  ONLY run this AFTER the NPU is verified GREEN on-device:
#       - model-qnn.pte is pushed to the app's llama/ dir
#       - the HTP .so libs are in jniLibs/arm64-v8a/
#       - the app's PROOF panel shows "Hexagon NPU · QNN"
#     Applying it before that leaves the app with NO working LLM (CPU fallback is gone).
#
set -euo pipefail
cd "$(dirname "$0")/../.."                       # repo root
PATCH=patches/cpu-removal/cpu-removal.patch

echo "== 1. verify the patch still applies cleanly =="
patch -p1 --dry-run < "$PATCH"

echo "== 2. apply =="
patch -p1 < "$PATCH"

echo ""
echo "Done — the LLM is now NPU-only (no CPU/XNNPACK fallback)."
echo "Rebuild + reinstall the app."
echo "Revert any time with:  patch -p1 -R < $PATCH"
