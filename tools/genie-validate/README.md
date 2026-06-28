# Genie NPU validation harness

Run **Qwen on the Hexagon NPU** with our **exact intake prompt** — *without* touching the app —
to validate the NPU + our prompts before tomorrow's clean ExecuTorch swap.

> **Why this exists.** AI Hub gives a **Genie** bundle (QNN HTP context binaries), not an
> ExecuTorch `.pte`. Rather than spend half a day on a throwaway Genie *app* integration, we
> use Qualcomm's own `genie-t2t-run` CLI to prove the model + our prompt work on the NPU. The
> app stays on its current path and gets the ExecuTorch `.pte` drop-in tomorrow.

---

## What you need (ask a Qualcomm rep at the event — fastest)

This is the **one unlock** for *both* tonight's Genie test **and** tomorrow's ExecuTorch path.
Ask the rep for the **QAIRT/QNN SDK** (or just these files), built for **SM8750 / Snapdragon 8 Elite**:

### A. The model — AI Hub Genie bundle  → put in `./qwen-genie-bundle/`
Download from AI Hub (or HF `qualcomm/Qwen3-4B`): runtime **Genie**, device **Snapdragon 8 Elite QRD**.
Unzip it; it must contain:
- `genie_config.json`
- the model context binary/binaries — `*.bin` (these ARE the NPU-compiled graphs)
- the tokenizer (`tokenizer.json`)

### B. The runtime libs — from the QAIRT SDK  → put in `./qairt-libs/`
| File | From (inside QAIRT SDK) | Needed by |
|---|---|---|
| `genie-t2t-run` | `bin/aarch64-android/` | Genie test only |
| `libGenie.so` | `lib/aarch64-android/` | Genie test only |
| `libQnnHtp.so` | `lib/aarch64-android/` | **both** |
| `libQnnSystem.so` | `lib/aarch64-android/` | **both** |
| `libQnnHtpV79Stub.so` | `lib/aarch64-android/` | **both** |
| `libQnnHtpPrepare.so` | `lib/aarch64-android/` | both (online prepare) |
| `libQnnHtpV79Skel.so` | `lib/hexagon-v79/unsigned/` | **both** |

> **Hexagon version.** SM8750 (Snapdragon 8 Elite) is expected to be **v79** → `...V79Stub.so` /
> `...V79Skel.so`. **Confirm it:** the QAIRT folder is named `hexagon-v<NN>`, and `genie_config.json`
> names the DSP arch. Match the Stub/Skel to whatever the bundle was compiled for — a mismatch is
> the #1 reason it silently fails to load on the NPU. (The harness just pushes whatever you drop in
> `./qairt-libs/`, so it's version-agnostic — you only have to get the right files.)
>
> The libs marked **both** are exactly what the ExecuTorch `.pte` path needs tomorrow — they go in
> `app/src/main/jniLibs/arm64-v8a/` (everything except `libGenie.so` + `genie-t2t-run`).

---

## Run it

```bash
cd tools/genie-validate
chmod +x run_genie_validation.sh

# 0) put the bundle in ./qwen-genie-bundle/ and the libs in ./qairt-libs/

# 1) SMOKE TEST first — proves the NPU loads at all (expect the model to print "READY")
PROMPT_FILE=./prompt.smoke.txt ./run_genie_validation.sh

# 2) THE REAL TEST — our intake prompt → expect a JSON object with facts/covered/prompts
./run_genie_validation.sh                       # uses prompt.user_only.txt (default)
```

### Which prompt file?
- **`prompt.user_only.txt`** (default) — just the checklist + transcript + output instruction.
  Use this **if `genie_config.json` already applies a Qwen chat template** (most AI Hub bundles do).
- **`prompt.full_chatml.txt`** — our *exact* assembled prompt, with the `<|im_start|>` tags + the
  Qwen3 no-think `<think></think>` seed + the `{"facts": [` prefill (identical to `PromptAssembler.assembleChatML`).
  Use this **only if** the config does *not* template (raw passthrough) — otherwise you'll double-wrap.
  ```bash
  PROMPT_FILE=./prompt.full_chatml.txt ./run_genie_validation.sh
  ```
  If the model echoes `<|im_start|>` tags or never stops → switch prompt files.

---

## What success looks like

- **`genie_output.txt`** — a single JSON object: `facts` (statement + source_quote), `covered`
  (checklist ids it found), `prompts` (follow-ups). That's our pipeline working on the NPU model.
- **`genie_npu_proof.txt`** — lines like `QnnHtp`, `Hexagon`, `fastrpc`, `GraphPrepare`. **This is
  the NPU proof.** A QNN HTP context binary can only execute on the Hexagon — if it produced output,
  it ran on the NPU. Empty here = it did not load on the NPU (check the Skel version above).

## What it proves / next step
A clean run validates: the device + QAIRT libs work, **and** Qwen produces good intake JSON for our
prompt on the NPU. Tomorrow the reps hand over the **ExecuTorch `.pte`** (built with the same QAIRT
SDK) + the **`both`** libs above → drop `model-qnn.pte` into the app's `llama/` dir + the libs into
`jniLibs/arm64-v8a/`, and the app auto-detects the NPU. ~2-minute change on our side.
