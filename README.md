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

It records the conversation, transcribes it on-device, attributes speakers by *content*
(the lawyer asks, the client narrates), and runs a small on-device LLM to produce **three outputs**:

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
Live transcript  (low-confidence spans flagged)
   │
   ▼  Content-based speaker attribution  (lawyer asks / client narrates)
Speaker-labeled transcript
   │
   ▼  matter-type checklist (data)  +  transcript  +  system prompt
On-device LLM  ·  Qwen3-1.7B  ·  ExecuTorch  →  Hexagon NPU (QNN)  /  CPU (XNNPACK) fallback
   │
   ▼
JSON  { facts[], missing[], prompts[] }
   │
   ▼  🔒 AES-256-GCM encrypted local store  +  themed PDF case report
```

The LLM stage is the technical centerpiece and runs through **ExecuTorch**. The app ships an
ExecuTorch QNN runtime and auto-detects a QNN-exported `.pte` to run the LLM on the **Hexagon NPU**;
absent that, it runs an XNNPACK `.pte` on **CPU** as the always-available floor.

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
| On-device LLM | Qwen3-1.7B (8da4w) via **ExecuTorch 1.3.1** |
| LLM runtime | **Qualcomm QNN → Hexagon NPU**; XNNPACK / CPU fallback |
| ASR | **Whisper** `base.en` via **whisper.cpp + ggml** (CPU/NEON), built from vendored sources |
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
