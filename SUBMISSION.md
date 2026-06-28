# Sub Rosa — Hackathon Submission

> Copy-paste fields for the submission form. Character counts validated against the form limits.

---

## Submission Title  *(min 5 / max 50)*

```
Sub Rosa — On-Device AI Legal Intake
```

---

## Short Description  *(min 50 / max 255)*

```
Sub Rosa records a lawyer's first client consultation and turns it into structured intake notes — facts with source quotes, a missing-information checklist, and follow-up prompts — running 100% on-device and offline on the phone's NPU. No cloud, ever.
```

---

## Long Description  *(min 100 words / 600–2000 chars)*

```
Sub Rosa is an Android app that turns a lawyer's first client consultation into clean, structured intake notes — entirely on the phone, fully offline, with no network calls at any point.

It records the conversation, transcribes it on-device with Whisper (whisper.cpp), attributes speakers by content (the lawyer asks, the client narrates), then runs a small on-device LLM — Qwen3-1.7B via ExecuTorch, exported to run on the Qualcomm Hexagon NPU through the QNN backend — to produce three outputs: (1) facts, each paired with the verbatim quote that supports it; (2) missing information — the standard intake items the client did NOT cover, computed as a true set-difference against a human-authored checklist; and (3) neutral follow-up prompts for the lawyer.

The core design principle: the model is a reasoning-over-provided-text engine, never a knowledge source. It never states the law. All legal scaffolding comes from human-authored, matter-type checklists injected into the prompt — so a 1B-class model stays accurate and safe with zero fine-tuning. The "missing items" scorecard is the killer feature for a real intake.

Privacy is structural, not promised: the app holds no INTERNET permission, so the OS itself blocks any socket, and an on-screen panel proves bytes-sent stays at zero. Audio is transcribed and discarded, never persisted, and all client data is encrypted at rest with AES-256-GCM via the Android Keystore. Live metrics expose the NPU path — model-load time, tokens/sec, and the active backend — making on-device inference visible and verifiable.
```

---

## Technologies Used

```
Android, Kotlin, Jetpack Compose, ExecuTorch, Qualcomm QNN, Hexagon NPU, XNNPACK, Qwen3-1.7B, On-device LLM, Whisper, whisper.cpp, ggml, On-device ASR, kotlinx.serialization, Android Keystore (AES-256-GCM), Snapdragon 8 Elite
```
