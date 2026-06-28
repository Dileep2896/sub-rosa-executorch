# Hexagon NPU runtime libraries (Qualcomm HTP)

✅ **Present (extracted 2026-06-27, QAIRT 2.47.0):** the **Hexagon v79** HTP libs for SM8750 are
already in this folder — `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so`,
`libQnnHtpPrepare.so`, `libQnnHtpV79Skel.so` — and all are **16 KB page-aligned**. The only thing
still needed to run on the NPU is the model: push **`model-qnn.pte`** to the device's `llama/` dir.
(When wiring the .pte, set `ADSP_LIBRARY_PATH` to the app's nativeLibraryDir at startup so the Skel
loads onto the Hexagon.) The notes below document where these came from.

Drop the Qualcomm QNN HTP `.so` libraries **here** to enable the LLM on the Hexagon NPU.
Anything in `app/src/main/jniLibs/arm64-v8a/` is packaged into the APK's native lib dir, where the
ExecuTorch QNN backend (`libqnn_executorch_backend.so`, already in the AAR) loads them.

## 1. Copy these from the Qualcomm QNN / QAIRT SDK (v2.29+)
From `$QAIRT/lib/aarch64-android/`:
- `libQnnHtp.so`
- `libQnnSystem.so`
- `libQnnHtpV79Stub.so`   ← Stub for the 8 Elite's Hexagon (confirm the version with the SDK / model card)
- `libQnnHtpPrepare.so`   (optional)

From `$QAIRT/lib/hexagon-v79/unsigned/`:
- `libQnnHtpV79Skel.so`   ← Skel for the device's Hexagon version

## 2. Push the QNN-exported model
```
DIR=/sdcard/Android/data/com.subrosa.app/files/llama
adb push qwen3-1_7b-qnn.pte  $DIR/model-qnn.pte
adb push tokenizer.bin       $DIR/tokenizer-qnn.bin   # only if it differs from the CPU tokenizer
```

## 3. Rebuild + run
The app **auto-detects `model-qnn.pte`** and runs the LLM on the NPU — the PROOF panel shows
**`Hexagon NPU · QNN`**, and logcat shows `QnnDsp` delegation. Remove `model-qnn.pte` to fall back to
CPU/XNNPACK. (This README is ignored by the build; only `.so` files are packaged.)
