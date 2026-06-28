# Sub Rosa — Model export & on-device wiring

This is the bridge between the app (already built + proven) and a real model. The app talks to
ExecuTorch through one file — `app/src/main/java/com/subrosa/app/llm/LlmEngine.kt` — and loads a
`.pte` + tokenizer from the app's **external files dir**. Producing that `.pte` is the only work left
to get real legal notes out of the app on CPU.

> ⚠️ Never commit weights or `.pte` files to this repo. Llama is license-gated and non-redistributable.
> The export runs in a *separate* workspace; only the device gets the `.pte` (via `adb push`).

---

## What the app expects (verified on the emulator)

- ExecuTorch runtime: **`org.pytorch:executorch-android:1.3.1`** (Maven AAR — XNNPACK/CPU built in).
  Bundled `.so`: `libexecutorch.so`, `libfbjni.so`, `libc++_shared.so` (arm64-v8a).
- On-device model files (pushed by `adb`, read by the app — no root, no storage permission needed):
  ```
  /sdcard/Android/data/com.subrosa.app/files/llama/model.pte
  /sdcard/Android/data/com.subrosa.app/files/llama/tokenizer.bin
  ```
- `LlmModule(modelPath, tokenizerPath, temperature)` → `load()` → `generate(prompt, seqLen, cb, echo=false)`.
- Tokenizer format is auto-detected from content — the **filename `tokenizer.bin` is fine** for a
  SentencePiece (`tokenizer.model`) *or* a tiktoken (Llama 3) tokenizer. Just push it as `tokenizer.bin`.

### The one constraint the smoke test surfaced
The open **stories110M** test model was exported with `max_seq_len = 128`. Our real prompt
(system + 10-item checklist + speaker-labeled transcript + JSON schema) is **~660 tokens**, so the
runner returned `Max seq length exceeded`. The app handled it gracefully (a "could not be parsed"
notice, no crash), but to get real notes the model must be exported with:

> **`--max_seq_length 1024`** (or higher). 1024 fits the prompt + a full JSON answer with headroom.

Everything else in the pipeline is already proven: lib load, model load (RSS ~730 MiB for the fp32
110M test model), tokenization, **token generation (~57–67 tok/s on the emulator CPU)**, streaming
callback, and accurate `onStats` metrics.

---

## Track B-CPU — XNNPACK export on the Mac (feeds the emulator + S25 CPU fallback)

Runs on macOS (Apple Silicon). This `.pte` works on the arm64 emulator **and** as the on-device CPU
fallback, so it's the safety net even if QNN isn't ready on demo day.

1. **Get access + weights.** Request `meta-llama/Llama-3.2-1B-Instruct` on Hugging Face (gated;
   approval can take hours–days — do this first). Download `consolidated.00.pth`, `params.json`,
   `tokenizer.model`.

2. **Set up ExecuTorch** (match the runtime — **tag `v1.3.1`** — so the `.pte` schema matches the AAR):
   ```bash
   git clone -b v1.3.1 https://github.com/pytorch/executorch.git
   cd executorch && git submodule sync && git submodule update --init
   ./install_executorch.sh            # or the documented env setup for this tag
   ```

3. **Export to a 4-bit XNNPACK `.pte`** (starting point — confirm flags against
   `examples/models/llama/README.md` at the v1.3.1 tag, as flags drift between releases):
   ```bash
   python -m examples.models.llama.export_llama \
     --model llama3_2 \
     --checkpoint /path/Llama-3.2-1B-Instruct/consolidated.00.pth \
     --params     /path/Llama-3.2-1B-Instruct/params.json \
     -kv --use_sdpa_with_kv_cache \
     -X --xnnpack-extended-ops \
     -qmode 8da4w --group_size 256 -d fp32 \
     --max_seq_length 1024 \
     --metadata '{"get_bos_id":128000,"get_eos_ids":[128009,128001]}' \
     --output_name model.pte
   ```
   Output: `model.pte` (~0.7–1.1 GB at 4-bit). Tokenizer = the downloaded `tokenizer.model`.

4. **Push to the emulator/device and run:**
   ```bash
   adb -s emulator-5554 shell mkdir -p /sdcard/Android/data/com.subrosa.app/files/llama
   adb -s emulator-5554 push model.pte        /sdcard/Android/data/com.subrosa.app/files/llama/model.pte
   adb -s emulator-5554 push tokenizer.model  /sdcard/Android/data/com.subrosa.app/files/llama/tokenizer.bin
   ```
   In the app: open **PROOF** → tap **ENGINE** to switch to `EXECUTORCH` (this also warms the model),
   then run a consultation. The Results screen should now show parsed facts/missing/prompts; the
   metrics panel shows the real backend + tokens/sec.

5. **If JSON is unstable** on real output, iterate the prompt in
   `app/.../data/notes/PromptAssembler.kt` (`SYSTEM` / `OUTPUT_INSTRUCTION`) and/or `seqLen` in
   `ExecuTorchNotesGenerator` — the defensive parser (`NotesJsonParser`) already strips fences,
   brace-matches, and salvages fields, so aim for "JSON-ish" and let the parser finish the job.

---

## Track C — QNN / Hexagon NPU export (Linux/cloud box, for device-day on the S25)

The QNN ahead-of-time toolchain is Linux-x86 only. Build this *before* the event on a cloud box.
**Pin the QNN SDK version** and use the same version's runtime `.so` on the device.

```bash
# inside ExecuTorch, with the Qualcomm AI Engine Direct SDK installed:
python examples/qualcomm/oss_scripts/llama/llama.py \
  --decoder_model llama3_2-1b_instruct \
  -m SM8750 \
  --model_mode kv --max_seq_len 1024 --ptq 16a4w
```
Produces a QNN `.pte` (~1.1–1.2 GB, 16a4w). Device-day: also build/obtain the QNN runtime `.so`
(`libQnnHtp*.so` + ExecuTorch QNN libs) into `jniLibs/arm64-v8a`, push the QNN `.pte` as `model.pte`,
flip to `EXECUTORCH`, and confirm the backend asserts **HEXAGON NPU · QNN** (not CPU fallback).

---

## Status

- ✅ App + ExecuTorch CPU path **built and proven on the emulator** (real token generation, metrics,
  graceful degradation). Runtime FAKE↔EXECUTORCH toggle + model warmup in place.
- ✅ **Notes pipeline validated with an open model** (Qwen3-1.7B, on the emulator): clean `Parsed`
  scorecard — 5 verified facts / 6 missing-checklist items / 1 follow-up prompt from the sample
  transcript, ~15s @ ~31 tok/s. **To swap in Llama 3.2 1B: push its `.pte` + tokenizer as
  `model.pte` / `tokenizer.bin`, AND change the `realGenerator` in `AppContainer` to
  `PromptStyle.LLAMA3`** (Llama uses the `<|begin_of_text|>` template, not ChatML).
- ⏳ Llama 3.2 1B XNNPACK `.pte` — blocked on HF access; export per Track B-CPU above.
- ✅ Whisper ASR — `libwhisper.so` built from source; JFK sample transcribes on-device. Live mic
  wired; verify on the S25 (emulator has no scripted mic input).
- ⏳ QNN/NPU — device-day (Track C).
