#!/usr/bin/env bash
#
# Sub Rosa — Genie NPU validation harness
# -----------------------------------------------------------------------------
# Pushes an AI Hub Qwen Genie bundle + the QAIRT runtime libs to the S25 Ultra
# and runs the LLM on the Hexagon NPU (HTP) with OUR EXACT intake prompt, then
# proves from logcat that it ran on the NPU.
#
# This does NOT touch the Android app. It answers two questions before we commit
# to any integration:
#   1. Do the device + QAIRT libs actually load a Genie model on the NPU?
#   2. Does our prompt yield good intake JSON on the NPU model?
#
# Prereqs (see README.md for the exact rep / QAIRT file list):
#   - BUNDLE_DIR : unzipped AI Hub Genie bundle (genie_config.json + *.bin + tokenizer)
#   - LIB_DIR    : QAIRT .so libs + the genie-t2t-run binary
#   - adb on PATH, phone in developer mode, USB-authorized
# -----------------------------------------------------------------------------
set -euo pipefail

# ---- config (override via env) ----------------------------------------------
SERIAL="${SERIAL:-R3CXC0804WH}"                          # S25 Ultra adb serial
BUNDLE_DIR="${BUNDLE_DIR:-./qwen-genie-bundle}"          # unzipped AI Hub Genie bundle
LIB_DIR="${LIB_DIR:-./qairt-libs}"                       # QAIRT .so libs + genie-t2t-run
PROMPT_FILE="${PROMPT_FILE:-./prompt.user_only.txt}"     # see README: user_only vs full_chatml
DEVDIR=/data/local/tmp/genie_bundle
ADB="adb -s ${SERIAL}"

echo "== 1. sanity: device + required files =="
${ADB} get-state
[ -f "${BUNDLE_DIR}/genie_config.json" ] || { echo "!! no genie_config.json in ${BUNDLE_DIR}"; exit 1; }
[ -f "${LIB_DIR}/genie-t2t-run" ]        || { echo "!! no genie-t2t-run in ${LIB_DIR} (QAIRT bin/aarch64-android)"; exit 1; }
[ -f "${LIB_DIR}/libGenie.so" ]          || { echo "!! no libGenie.so in ${LIB_DIR} (QAIRT lib/aarch64-android)"; exit 1; }
[ -f "${PROMPT_FILE}" ]                  || { echo "!! no prompt file ${PROMPT_FILE}"; exit 1; }
echo "   bundle : ${BUNDLE_DIR}"
echo "   libs   : ${LIB_DIR}"
echo "   prompt : ${PROMPT_FILE}"

echo "== 2. push bundle + libs + prompt to ${DEVDIR} =="
${ADB} shell "rm -rf ${DEVDIR} && mkdir -p ${DEVDIR}"
${ADB} push "${BUNDLE_DIR}/." "${DEVDIR}/" >/dev/null
${ADB} push "${LIB_DIR}/."    "${DEVDIR}/" >/dev/null
${ADB} push "${PROMPT_FILE}"  "${DEVDIR}/prompt.txt" >/dev/null
${ADB} shell "chmod 0755 ${DEVDIR}/genie-t2t-run"

echo "== 3. clear logcat (so section 5 is a clean NPU proof) =="
${ADB} logcat -c

echo "== 4. run the LLM on the NPU with OUR prompt =="
# NOTE on quoting: the device-side  PROMPT="$(cat prompt.txt)"  captures the file's
# literal bytes (the prompt contains many "  double quotes), and  -p "$PROMPT"  passes
# them as a single arg without the shell re-parsing them. ADSP_LIBRARY_PATH is what
# routes execution onto the Hexagon DSP/NPU.
${ADB} shell "cd ${DEVDIR} && \
  export LD_LIBRARY_PATH=${DEVDIR}:\$LD_LIBRARY_PATH && \
  export ADSP_LIBRARY_PATH=${DEVDIR} && \
  PROMPT=\"\$(cat prompt.txt)\" && \
  ./genie-t2t-run -c genie_config.json -p \"\$PROMPT\"" | tee genie_output.txt

echo ""
echo "== 5. NPU PROOF: HTP / QnnDsp backend init in logcat =="
${ADB} logcat -d | grep -iE "QnnHtp|QnnDsp|Hexagon|HTP|fastrpc|GraphPrepare" | head -40 | tee genie_npu_proof.txt

echo ""
echo "-----------------------------------------------------------------------------"
echo "Model output  -> genie_output.txt   (expect: a JSON object, facts/covered/prompts)"
echo "NPU evidence  -> genie_npu_proof.txt (expect: QnnHtp / Hexagon / fastrpc lines)"
echo "If section 5 is EMPTY, the model did NOT initialize on the NPU — most likely the"
echo "Skel version doesn't match the bundle's Hexagon arch (see README, 'Hexagon version')."
