# Sub Rosa — Hackathon Submission

> Copy-paste fields for the submission form. Character counts validated against the form limits.

---

## Submission Title  *(min 5 / max 50)*

```
Sub Rosa: On-Device AI Legal Intake
```

---

## Short Description  *(min 50 / max 255)*

```
Sub Rosa records a lawyer's first client meeting and turns it into clear intake notes: facts with the quotes that back them, the standard intake items the client never mentioned, and follow-up questions. Runs on the phone, fully offline. No cloud.
```

---

## Long Description  *(min 100 words / 600–2000 chars)*

```
Sub Rosa is an Android app that turns a lawyer's first client meeting into clear intake notes. It runs entirely on the phone and never goes online.

It records the conversation and transcribes it on the device with Whisper. A small language model, Qwen3-1.7B running with ExecuTorch on the Qualcomm Hexagon NPU, then produces three things: the facts, each shown next to the exact words from the transcript that back it up; the missing items, the standard intake points the client did not mention, found by comparing the talk against a checklist; and a few neutral follow-up questions to ask.

The model only reads and sorts what it is given. It never states the law or gives advice. The legal knowledge lives in checklists written by people, not in the model's weights, so a small model stays accurate with no training step.

Privacy is built in. The app has no internet permission, so the phone blocks any connection, and an on-screen panel shows nothing was sent. Audio is never saved, and all client data is encrypted on the device with AES-256-GCM.
```

---

## Technologies Used

```
Android, Kotlin, Jetpack Compose, ExecuTorch, Qualcomm QNN, Hexagon NPU, XNNPACK, Qwen3-1.7B, On-device LLM, Whisper, whisper.cpp, ggml, On-device ASR, kotlinx.serialization, Android Keystore (AES-256-GCM), Snapdragon 8 Elite
```

---

## Other fields

- **Repository:** https://github.com/Dileep2896/sub-rosa-executorch
- **License:** MIT
