#!/usr/bin/env bash
#
# Restore Sub Rosa's on-device models after a reinstall / new device / accidental uninstall.
# The models live in the app's EXTERNAL files dir, which Android deletes on uninstall — so we keep a
# copy on the Mac (~/subrosa-models) and push them back in seconds with this script.
#
#   usage:  bash tools/push-models.sh
#   needs:  the phone connected via adb (override serial with  SERIAL=XXXX bash tools/push-models.sh)
#
set -euo pipefail
SERIAL="${SERIAL:-R3CXC0804WH}"
SRC="${SRC:-$HOME/subrosa-models}"
DEST="/sdcard/Android/data/com.subrosa.app/files/llama"
ADB="adb -s ${SERIAL}"

echo "Restoring models  ${SRC}  →  ${DEST}  (device ${SERIAL})"
${ADB} get-state >/dev/null
${ADB} shell "mkdir -p ${DEST}" 2>/dev/null || true

pushed=0
for f in model-qnn.pte model.pte tokenizer.bin whisper-model.bin; do
  if [ -f "${SRC}/${f}" ]; then
    echo "  → ${f}  ($(du -h "${SRC}/${f}" | cut -f1))"
    ${ADB} push "${SRC}/${f}" "${DEST}/${f}" >/dev/null
    pushed=$((pushed + 1))
  else
    echo "  ⚠ ${SRC}/${f} not found — skipped"
  fi
done

echo ""
echo "Pushed ${pushed} file(s). On device now:"
${ADB} shell "ls -la ${DEST}"
echo ""
echo "Relaunch the app — it auto-detects model.pte/whisper-model.bin and warms them at startup."
