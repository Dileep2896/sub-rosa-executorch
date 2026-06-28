# Sub Rosa — NPU handoff (for the Qualcomm engineer)

**Ask:** help us run **Qwen3-1.7B** (text-gen LLM) on the **Hexagon NPU** of a
**Samsung Galaxy S25 Ultra → SoC `SM8750` (Snapdragon 8 Elite)** via **ExecuTorch + QNN**, inside our
existing Android app.

**Our app is already wired for it — we just need the model + libs.**
- We ship the **ExecuTorch 1.3.1 QNN AAR** (`executorch.aar`, `1.3.1-qnn`); `libqnn_executorch_backend.so`
  is already in our APK and is 16 KB page-aligned.
- We use the standard `org.pytorch.executorch.extension.llm.LlmModule` API (no custom runner).
- The app **auto-detects a QNN model** named `model-qnn.pte` and flips to the NPU; otherwise it runs CPU.

So this is a drop-in for us. We need **2 deliverables** (or just hand us your prebuilt kit — Option A).

---

## Option A (fastest, ~2 min): hand us your prebuilt ExecuTorch-QNN kit
If you have the reference **ExecuTorch QNN Llama/Qwen ≤1.7B Android kit** (the official LlamaDemo Hexagon
demo), we'll take its **`.pte` + tokenizer + HTP `.so` libs**. Any supported ~1B decoder is fine
(Llama-3.2-1B, Qwen3-1.7B, …). Keep it ≤ ~1.7B — the device has **~10.9 GB** usable RAM.

## Option B (~30 min): export our model on a Linux box with the QAIRT/QNN SDK
Per the ExecuTorch Qualcomm tutorial (`examples/qualcomm/oss_scripts/llama/`):

```bash
python examples/qualcomm/oss_scripts/llama/llama.py \
  --build_folder build-android \
  --soc_model SM8750 \
  --decoder_model qwen3-1_7b \
  --model_mode kv \
  --max_seq_len 1024 \
  --checkpoint <qwen3-1.7B weights> \
  --params params.json \
  --tokenizer_model <qwen3 tokenizer>
```
- `--model_mode kv` keeps host RAM modest; `--model_mode hybrid --prefill_ar_len 128` is faster on-device
  but needs ~80 GB host RAM.
- If you hit the **4 GB per-context HTP limit**, add `--num_sharding 2` (or more).
- Please export with an **ExecuTorch version compatible with runtime 1.3.1** so the `.pte` loads against our
  `1.3.1-qnn` AAR.

---

## What we need back (3 files) and where they go

| # | File | Source | Destination |
|---|------|--------|-------------|
| 1 | **`model-qnn.pte`** (exported QNN model) | export output | `adb push … → /sdcard/Android/data/com.subrosa.app/files/llama/model-qnn.pte` |
| 2 | **tokenizer** (only if different from ours) | model | `… /files/llama/tokenizer-qnn.bin` |
| 3 | **HTP runtime `.so` libs** (list below) | `$QNN_SDK_ROOT` | we copy into the app's `jniLibs/arm64-v8a/` and rebuild |

**HTP libs (item 3) — please confirm the Hexagon HTP version for `SM8750` (e.g. `V79`) so we grab the right Stub/Skel:**

From `$QNN_SDK_ROOT/lib/aarch64-android/`:
- `libQnnHtp.so`
- `libQnnSystem.so`
- `libQnnHtpV<NN>Stub.so`
- `libQnnHtpPrepare.so` *(if present)*

From `$QNN_SDK_ROOT/lib/hexagon-v<NN>/unsigned/`:
- `libQnnHtpV<NN>Skel.so`

---

## One question for you
- **What is the Hexagon HTP architecture version for `SM8750`?** (so we pull the matching `Stub`/`Skel`).

## Bench sanity check (optional)
`qnn_executor_runner` / `genie-t2t-run` on the `.pte` should show **`QnnDsp` HTP delegation** in `adb logcat`.

> Once we have files 1–3, flipping the app to the NPU (and showing a green **`Hexagon NPU · QNN`** backend
> chip) is a ~2-minute change on our side.
