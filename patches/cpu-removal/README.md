# CPU-removal patch (NPU-only LLM) — staged, not applied

Ready-to-apply patch that makes the **LLM run only on the Hexagon NPU** and removes the CPU/XNNPACK
fallback. **Staged on 2026-06-27, dry-run verified clean. NOT yet applied** (we hold zero NPU
artifacts right now — removing CPU before the NPU works would leave the app with no LLM).

## ⚠️ When to apply
**Only after the NPU is verified green on-device:** `model-qnn.pte` pushed, HTP `.so` libs in
`jniLibs/arm64-v8a/`, and the app's PROOF panel shows **`Hexagon NPU · QNN`**. Until then, leave the
CPU path in — it's the demo's only fallback.

## What it changes (2 files, 2 hunks)
- **`AppContainer.kt`** — `llmEngine` loads **only** `model-qnn.pte` on the NPU. The
  `model.pte` / "CPU · XNNPACK" branch is gone; the backend label is hard-set to `Hexagon NPU · QNN`.
- **`LlmEngine.kt`** — default `backendLabel` flips from `ExecuTorch · CPU (XNNPACK)` →
  `Hexagon NPU · QNN`. **Bonus fix:** `load()` re-sets the label from this default, which previously
  overwrote the NPU label with "CPU" even on the NPU. Now it can't lie.

## What it deliberately does NOT touch
- **Whisper ASR + sherpa-onnx diarization stay on CPU** — by design (no NPU/QNN recipe exists for
  them). "Remove CPU" here means the **LLM** only.
- **`build.gradle.kts`** — unchanged. The vendored `executorch-qnn-1.3.1.aar` stays (it *is* the QNN
  runtime; it bundles XNNPACK too, but the app no longer uses the CPU code path).
- The guided-demo path's own `"… (demo)"` label is left alone (it's canned, not real inference).

## Apply / revert
```bash
# from repo root, after NPU is green:
./patches/cpu-removal/apply.sh          # dry-runs then applies
# or manually:
patch -p1 < patches/cpu-removal/cpu-removal.patch

# revert:
patch -p1 -R < patches/cpu-removal/cpu-removal.patch
```

## Verify after applying
1. App builds.
2. PROOF panel reads **`Hexagon NPU · QNN`** (and `logcat` shows `QnnDsp`/`QnnHtp` delegation).
3. Sanity: temporarily remove `model-qnn.pte` → the LLM now **errors clearly** instead of silently
   running on CPU. That's the point. Put it back before demoing.
