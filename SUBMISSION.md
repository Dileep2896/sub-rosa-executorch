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

The app records the conversation and transcribes it on the device with Whisper (whisper.cpp). It works out who is speaking from what they say, since the lawyer asks the questions and the client tells the story. It then runs a small language model on the device, Qwen3-1.7B with ExecuTorch on the Qualcomm Hexagon NPU through the QNN backend, and produces three things. First, the facts, each one shown next to the exact words from the transcript that back it up. Second, the missing items: the standard things a lawyer needs for this kind of matter that the client did not mention, found by comparing the conversation against a checklist. Third, a few neutral follow-up questions the lawyer might want to ask.

The model is only allowed to read and sort what it was given. It never states the law or gives advice. Every piece of legal knowledge lives in checklists written by people, not in the model's weights, so a small model stays accurate and there is no training step.

Privacy is built in. The app has no internet permission, so the phone itself stops it from opening a connection, and a panel on screen shows that nothing was sent. Audio is transcribed and thrown away, never saved. All client data is encrypted on the device with AES-256-GCM using the Android Keystore. A metrics panel shows how the model is running: load time, tokens per second, and which backend is active.
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
