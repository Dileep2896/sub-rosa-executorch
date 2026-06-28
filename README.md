<div align="center">

# Sub Rosa

**On-device AI note-taker for a lawyer's first client consultation.**
Records → transcribes → extracts structured intake notes — **100% on-device, fully offline.**

Built for the **Qualcomm × Meta ExecuTorch Hackathon** (June 27–28, 2026).
Target device: **Samsung Galaxy S25 Ultra** · Snapdragon 8 Elite · Hexagon NPU.

</div>

---

## What it does

Sub Rosa turns a lawyer's first client consultation into clean, structured intake notes —
entirely on the phone, with **no network calls at any point**.

It records the conversation, transcribes it on-device (Whisper), diarizes the speakers (pyannote, via
sherpa-onnx), and runs a small on-device LLM **on the Hexagon NPU** to produce **three outputs**:

1. **Facts** — each extracted fact paired with the **verbatim quote** from the transcript that supports it.
2. **Missing information** — the standard intake items the client did **not** cover, computed as a true
   **set-difference** against a human-authored, matter-type checklist. *(This is the killer feature.)*
3. **Follow-up prompts** — short, neutral interview questions for the lawyer — never legal conclusions.

### The guiding principle

> The model is a **reasoning-over-provided-text engine, never a knowledge source.**

It never states the law. All legal scaffolding lives in **human-authored checklists** injected into the
prompt at runtime — so a 1B-class model stays accurate and safe with **zero fine-tuning**. The app's legal
competence equals the quality of those checklists, which are auditable data files, not opaque weights.

---

## How it works (on-device pipeline)

```
🎙️ Microphone
   │
   ▼  Whisper base.en  ·  whisper.cpp + ggml  ·  CPU/NEON
Live transcript  (timestamped lines)
   │
   ▼  Speaker diarization  ·  pyannote + sherpa-onnx  ·  voiceprints (who-spoke-when)
Speaker-labeled transcript
   │
   ▼  matter-type checklist (data)  +  transcript  +  system prompt
On-device LLM  ·  Qwen3-1.7B  ·  ExecuTorch  →  Hexagon NPU (QNN)   [ CPU/XNNPACK = fallback only ]
   │
   ▼
JSON  { facts[], missing[], prompts[] }
   │
   ▼  🔒 AES-256-GCM encrypted local store  +  themed PDF case report
```

The LLM stage is the technical centerpiece and runs on the **Hexagon NPU** through ExecuTorch's QNN
backend. `NpuRunnerEngine` drives Qualcomm's `qnn_llama_runner` over a QNN-exported `model-qnn.pte`;
`FallbackTextEngine` wraps it and drops to an XNNPACK CPU engine (red `CPU FALLBACK` chip) only if the
DSP fails. The app auto-detects `model-qnn.pte` at launch (`AppContainer.llmOnNpu`) — NPU when present,
CPU as the safety net.

📐 Full architecture, with eight Mermaid diagrams, lives in **[`ARCHITECTURE.md`](ARCHITECTURE.md)**.

---

## Privacy — structural, not promised

- **No `INTERNET` permission** in the manifest → the OS itself blocks any socket. An on-screen privacy
  panel proves **bytes-sent = 0** and shows a live "outbound socket · BLOCKED BY OS" probe.
- **Audio is never persisted** — transcribe-and-discard.
- **All client data is encrypted at rest** (AES-256-GCM via the Android Keystore); `allowBackup="false"`.
- **Biometric / device-credential app-lock** gates launch.

## Live metrics (the NPU is visible)

The PROOF panel surfaces real numbers: model-load time, **tokens/sec**, ASR latency, and the active
**backend label** (`Hexagon NPU · QNN` / `ExecuTorch · CPU (XNNPACK)`) — so on-device inference is verifiable, not claimed.

---

## Tech stack

| Layer | Choice |
|---|---|
| App | Android (Kotlin) · Jetpack Compose · MVVM · manual DI |
| On-device LLM | Qwen3-1.7B via **ExecuTorch 1.3.1** |
| LLM runtime (primary) | **Qualcomm QNN → Hexagon NPU** via `qnn_llama_runner` (`model-qnn.pte`) |
| LLM runtime (fallback) | ExecuTorch **XNNPACK / CPU** (`model.pte`) — only on a DSP failure |
| ASR | **Whisper** `base.en` via **whisper.cpp + ggml** (CPU/NEON), built from vendored sources |
| Speaker diarization | **pyannote** (segmentation-3.0 + wespeaker) via **sherpa-onnx** (ONNX Runtime, CPU) |
| Output | Strict JSON `{ facts, missing, prompts }`; defensive brace-match parser |
| Persistence | `kotlinx.serialization` JSON, AES-256-GCM (Android Keystore) |
| Reporting | Native `android.graphics.pdf` themed case report (no library, no network) |

---

## Building from source

> **Models and large vendored binaries are not in this repo** — Llama/Qwen weights are non-redistributable,
> Qualcomm's HTP libraries are proprietary, and model `.pte` files are gigabytes. The repo is **source-only**;
> here is how to put everything back.

1. **ExecuTorch + sherpa-onnx AARs** → `app/libs/`
   `executorch-qnn-1.3.1.aar`, `sherpa-onnx-1.13.3.aar` (referenced by `app/build.gradle.kts`).
2. **Qualcomm QNN / Hexagon HTP `.so` libs** → `app/src/main/jniLibs/arm64-v8a/`
   See [`app/src/main/jniLibs/arm64-v8a/README.md`](app/src/main/jniLibs/arm64-v8a/README.md) for the exact
   files (`libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`,
   `libqnn_executorch_backend.so`) and the `qnn_llama_runner` (shipped as `libqnn_llama_runner.so`).
3. **Build** (system JDK 20 won't work; use a JDK 17+ such as Android Studio's bundled JBR):
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:assembleDebug          # builds libwhisper.so from vendored sources too
   ./gradlew :app:testDebugUnitTest      # unit tests
   ./gradlew :app:installDebug           # install over-the-top (NEVER adb uninstall — it wipes models + data)
   ```
4. **Push the models** to the device — see **[`MODELS.md`](MODELS.md)** (export recipe + `tools/push-models.sh`).

The app runs end-to-end on the **arm64 Android emulator** (Apple Silicon) on CPU before any device exists;
only the **QNN/NPU backend is device-bound**.

---

## Proof — the on-device code

Every model runs locally. Here is exactly where each part lives, so the claims are auditable.

### LLM on the Hexagon NPU (Qualcomm QNN)
- **[`llm/NpuRunnerEngine.kt`](app/src/main/java/com/subrosa/app/llm/NpuRunnerEngine.kt)** — the NPU path.
  Execs Qualcomm's `qnn_llama_runner` (shipped as `libqnn_llama_runner.so`) as a subprocess per
  generation, with `LD_LIBRARY_PATH` / `ADSP_LIBRARY_PATH` pointed at the QNN HTP libs; runs
  `--eval_mode 0` (kv) `--decoder_model_version qwen3`, and parses **real load-ms + tokens/sec** from the
  runner's `PyTorchObserver` line. It drives `model-qnn.pte`'s `kv_forward` contract, which the generic
  ExecuTorch `LlmModule` cannot.
- **[`llm/FallbackTextEngine.kt`](app/src/main/java/com/subrosa/app/llm/FallbackTextEngine.kt)** — NPU is
  primary; on the first DSP failure it flips to CPU for the session and sets the backend chip to a red
  `CPU FALLBACK` (so the demo stays honest, never a lie).
- **[`llm/LlmEngine.kt`](app/src/main/java/com/subrosa/app/llm/LlmEngine.kt)** — the ExecuTorch
  XNNPACK/CPU engine (`LlmModule` over `model.pte`) = the fallback / always-available floor.
- **Wiring & auto-detect:** [`di/AppContainer.kt`](app/src/main/java/com/subrosa/app/di/AppContainer.kt)
  → `llmEngine` / `llmOnNpu` (true when `model-qnn.pte` is present).
- **HTP libs:** [`app/src/main/jniLibs/arm64-v8a/`](app/src/main/jniLibs/arm64-v8a/) (see its README).
  Export recipe: [`NPU_HANDOFF.md`](NPU_HANDOFF.md), [`MODEL_EXPORT.md`](MODEL_EXPORT.md).
- **Proven:** ~40 tok/s decode, ~0.74 s load on Hexagon **v79** (SM8750) via `qnn_llama_runner`.

### Whisper ASR (whisper.cpp)
- **[`data/asr/WhisperEngine.kt`](app/src/main/java/com/subrosa/app/data/asr/WhisperEngine.kt)** — boundary
  over whisper.cpp; `selfTest(wav)` proves the native lib + ggml model + JNI bridge run on-device.
- **[`data/asr/WhisperTranscriber.kt`](app/src/main/java/com/subrosa/app/data/asr/WhisperTranscriber.kt)** /
  **[`AudioRecorder.kt`](app/src/main/java/com/subrosa/app/data/asr/AudioRecorder.kt)** — windowed live
  transcription over the 16 kHz mic Flow.
- **JNI bridge:** [`com/whispercpp/whisper/LibWhisper.kt`](app/src/main/java/com/whispercpp/whisper/LibWhisper.kt).
- **Native build:** [`app/src/main/cpp/CMakeLists.txt`](app/src/main/cpp/CMakeLists.txt) builds
  `libwhisper.so` (whisper.cpp 1.9.1 + ggml, CPU/NEON, forced `-O3`, 16 KB page-aligned) from the vendored
  sources in `app/src/main/cpp/whisper.cpp/`.

### Speaker diarization (pyannote via sherpa-onnx)
- **[`data/asr/OnnxDiarizer.kt`](app/src/main/java/com/subrosa/app/data/asr/OnnxDiarizer.kt)** — pyannote
  `segmentation-3.0` + wespeaker embedding + fast clustering over sherpa-onnx (ONNX Runtime, CPU); the
  "people in the room" count seeds `numClusters`. Runs as a post-Stop pass.
- **[`data/asr/LiveSpeakerId.kt`](app/src/main/java/com/subrosa/app/data/asr/LiveSpeakerId.kt)** — live
  per-segment voiceprint assignment during capture (cosine ≥ 0.7).
- Self-tests: `AppContainer.diarizerSelfTest()` / `liveSpeakerIdSelfTest()`.

### Privacy (no network, by construction)
- **[`metrics/PrivacyMonitor.kt`](app/src/main/java/com/subrosa/app/metrics/PrivacyMonitor.kt)** — verifies
  the app does **not** declare `INTERNET`, shows a per-session byte delta, and **actively probes** an
  outbound socket (`connect 8.8.8.8:53` → `EACCES` → `BLOCKED`).
- No `INTERNET` permission + `allowBackup=false` in `AndroidManifest.xml`; AES-256-GCM at rest in
  [`data/crypto/`](app/src/main/java/com/subrosa/app/data/crypto/) via the Android Keystore.

---

## Testing on a device

**Prereqs:** Android Studio / SDK, JDK 17+, and an **arm64 device**. The Hexagon-NPU LLM needs an
S25-class **SM8750**; the CPU path runs on any arm64 phone or the Apple-Silicon emulator.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug          # install over-the-top — NEVER `adb uninstall` (it wipes models + data)
bash tools/push-models.sh            # pushes model-qnn.pte / model.pte / tokenizer.bin / whisper-model.bin
```

Models land in `/sdcard/Android/data/com.subrosa.app/files/llama/`. The app auto-detects them at launch:
`model-qnn.pte` present → **Hexagon NPU**; otherwise `model.pte` → **CPU**. (Diarization also reads
`diarize-segmentation.onnx` + `diarize-embedding.onnx` from that folder.)

Confirm each engine from logcat:

```bash
# LLM on the NPU — exit=0 + a PyTorchObserver line means the NPU ran in-app:
adb logcat | grep -E "LlmEngine|NpuRunnerEngine|npu run exit|PyTorchObserver|FallbackTextEngine"
# Whisper ASR self-test:
adb logcat | grep -E "WhisperEngine|selfTest"
# pyannote diarization self-tests:
adb logcat | grep -E "OnnxDiarizer|DiarizerSelfTest|LiveSpkSelfTest"
# notes pipeline (facts / missing / prompts):
adb logcat | grep -E "NotesSelfTest"
```

In-app diagnostics (exposed on `AppContainer`): `notesSelfTest()`, `diarizerSelfTest()`,
`liveSpeakerIdSelfTest()`, `warmupTranscriber()`, `reportSelfTest()`.

> **NPU note — retail vs. engineering devices.** `qnn_llama_runner` loads the HTP Skel through the FastRPC
> **unsigned** protection domain. `adb shell` can use it (the shell run does ~40 tok/s), but a **retail**
> S25 blocks an untrusted app from the unsigned PD, so in-app NPU needs a device that permits it — i.e.
> where `adb shell setprop vendor.fastrpc.process.attrs 1` succeeds (eng / userdebug / QRD / rooted). On a
> retail unit the app cleanly **falls back to CPU/XNNPACK**. Details in [`NPU_HANDOFF.md`](NPU_HANDOFF.md).

---

## Repository map

| Path | What's there |
|---|---|
| `app/src/main/java/com/subrosa/app/` | The app — `ui/`, `data/` (asr · notes · crypto · stores · report), `llm/`, `di/`, `domain/` |
| `app/src/main/cpp/` | Vendored **whisper.cpp + ggml** sources → `libwhisper.so` via CMake |
| `app/src/main/assets/` | Human-authored `checklists.json` + sample fixtures + demo audio |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Eight Mermaid diagrams of the whole system |
| [`MODELS.md`](MODELS.md) · [`MODEL_EXPORT.md`](MODEL_EXPORT.md) · [`NPU_HANDOFF.md`](NPU_HANDOFF.md) | Model export, backup/restore, and NPU bring-up |
| [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) | ~3-minute presenter runbook |
| [`sub-rosa-build-brief.md`](sub-rosa-build-brief.md) | The original product/engineering brief |

---

## Known limitations (by design)

- **Context window** — a full consultation can overflow a 1B-class model's context; demo sessions are kept short.
- **ASR errors** — mitigated by evidence-linked quotes + low-confidence flags + scripted/typed fallbacks, not eliminated.
- **Checklist coverage** — the app is exactly as thorough as its human-authored checklists. Intentional and controllable.
- **Local storage hardening** — encrypted at rest, but full key-management / secure-export is roadmap, not MVP.

---

## License

Released under the **MIT License** — see [`LICENSE`](LICENSE). Vendored third-party components
(whisper.cpp, ggml, ExecuTorch, sherpa-onnx, Qualcomm QNN libraries) retain their own respective licenses.
Model weights (Llama / Qwen) are **not** included and are governed by their own model licenses.
