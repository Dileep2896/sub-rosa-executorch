# Sub Rosa — on-device models (backup · restore · re-create)

The app runs three models **entirely on-device**. They are **not in git** (too large + private) and live in
the app's storage, which Android **deletes on uninstall**. This doc is how to never lose them — and how to
re-create them if you do.

## The models

| File (on device) | What it is | ~Size | Source |
|---|---|---|---|
| `model.pte` | Qwen3-1.7B · XNNPACK int4 (8da4w) — the notes + speaker-attribution LLM | ~1.3 GB | exported (below) |
| `tokenizer.bin` | Qwen3 tokenizer (HF `tokenizer.json`) | ~11 MB | ships with the model |
| `whisper-model.bin` | Whisper `base.en` ggml — the ASR | ~148 MB | HF download |
| `model-qnn.pte` *(optional)* | Qwen3 QNN export for the Hexagon **NPU** | ~1.3 GB | Qualcomm reps / QNN export |

**On-device path:** `/sdcard/Android/data/com.subrosa.app/files/llama/`
The app **auto-detects** these at startup and warms them — and flips to the **NPU** automatically if
`model-qnn.pte` is present (else it runs `model.pte` on CPU/XNNPACK).

---

## ⚠️ THE GOLDEN RULE — never `adb uninstall`

Uninstall deletes **both** the models **and** your encrypted clients/cases. To update the app — even for a
manifest change — **reinstall over the top**, which keeps everything:

```bash
./gradlew :app:installDebug        # or Android Studio "Run", or:  adb install -r app-debug.apk
```

> "Apply Changes" / hot-reload does **not** pick up manifest changes — but a *normal reinstall* does.
> A plain reinstall (never an uninstall) is all you ever need.

---

## Restore after a reinstall / new device  (≈ 10 seconds)

The models are backed up on the Mac at **`~/subrosa-models/`**. Push them back:

```bash
bash tools/push-models.sh          # override device:  SERIAL=XXXX bash tools/push-models.sh
```

Then relaunch the app.

---

## Re-create from scratch (only if the Mac backup is *also* gone)

### Whisper `base.en`
```bash
curl -fL -o ~/subrosa-models/whisper-model.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin
```

### Qwen3 LLM (`model.pte` + `tokenizer.bin`)
Export with **executorch 1.3.1**, which matches the app's runtime AAR — a newer ExecuTorch would produce a
`.pte` the app can't load (`optimum-executorch` 1.1.0 pulls exactly 1.3.1):

```bash
python3 -m venv ~/.subrosa-export-venv
source ~/.subrosa-export-venv/bin/activate    # IMPORTANT: puts the bundled `flatc` on PATH (else serialize fails)
pip install optimum-executorch
optimum-cli export executorch \
  --model Qwen/Qwen3-1.7B --task text-generation --recipe xnnpack \
  --use_custom_sdpa --use_custom_kv_cache \
  --qlinear 8da4w --qlinear_group_size 32 --qembedding 8w --dtype float32 \
  --output_dir ~/qwen3_export
```
Then stage + push (the export takes ~25–40 min — mostly the 3.4 GB weight download):
```bash
cp ~/qwen3_export/model.pte ~/subrosa-models/model.pte
# optimum-executorch emits only model.pte — grab the tokenizer straight from HF (~11 MB):
curl -fL -o ~/subrosa-models/tokenizer.bin \
  https://huggingface.co/Qwen/Qwen3-1.7B/resolve/main/tokenizer.json
bash tools/push-models.sh
```

### NPU model (`model-qnn.pte`)
Comes from the Qualcomm reps (or the QNN export — see `NPU_HANDOFF.md`). Drop it into `~/subrosa-models/`
too and it'll be pushed + auto-detected. The HTP `.so` libs it needs are already in
`app/src/main/jniLibs/arm64-v8a/`.

---

## Why your data can't be backed up externally

Clients / cases / sessions are **AES-256-GCM encrypted with an Android Keystore key** — the privacy
guarantee (nothing leaves the device; `android:allowBackup="false"` blocks cloud backup). So they survive a
**reinstall-over** but not an **uninstall**, and they can't be copied off the phone. For resilience to a
lost/wiped device, the app would need an **in-app encrypted export/import** (a `.subrosa` backup file) —
not built yet.
