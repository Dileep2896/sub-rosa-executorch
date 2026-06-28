# Sub Rosa — Build Brief for Claude Code

> On-device AI note-taker for a lawyer's first client consultation.
> Built for the Qualcomm x Meta ExecuTorch Hackathon (June 27–28, 2026).
> Target device: Samsung Galaxy S25 Ultra (Snapdragon 8 Elite, 12GB RAM).

---

## 0. Read this first — what we are building and what we are NOT

**We are building:** an Android app that records a client consultation, transcribes it on-device, and uses a small on-device LLM to produce structured intake notes. Everything runs locally, offline, on the phone's NPU.

**The three outputs:**
1. **Facts from transcript** — each extracted fact paired with the verbatim quote that supports it.
2. **Missing information** — a checklist of standard intake items the client did NOT cover (the killer feature).
3. **Prompts for lawyer review** — short follow-up questions, phrased as interview prompts, never as legal conclusions.

**We are explicitly NOT building** (do not spend time here):
- Any feature where the model states the law, cites statutes, or gives legal advice.
- Model training or fine-tuning. (See §4 — all domain knowledge is supplied via prompt, not weights.)
- Speaker diarization ML. (Solved with a tap UX — see §3.)
- Cloud anything. No network calls at inference time, by design.
- Practice-management integrations, encrypted storage, auth hardening. (Roadmap only; mention in README.)

**Guiding principle:** the model is a *reasoning-over-provided-text engine*, never a knowledge source. Facts come from the transcript. Legal scaffolding comes from human-authored checklists injected into the prompt. The model only matches and reformats. This is what keeps a 1B model accurate and safe.

---

## 1. Architecture

```
Microphone
   |
On-device ASR (Whisper)            <- lawyer taps to mark speaker turns
   |
Live transcript (low-confidence spans flagged)
   |
[ matter-type checklist (data) ] + transcript + system prompt
   |
On-device LLM (Llama 3.2 1B, ExecuTorch + Qualcomm QNN backend)
   |
JSON: { facts[], missing[], prompts[] }
   |
Local storage  ->  export only on explicit user action
```

Two on-device model stages. The LLM stage is the technical centerpiece and must run on the NPU via QNN/ExecuTorch (this is the hackathon's 40% criterion). See §5 for the ASR routing decision.

---

## 2. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| App | Android (Kotlin) | Native; demoed on S25 Ultra |
| LLM | Llama 3.2 **1B** Instruct | 1B is the default. 3B wants 16GB; device has 12GB. 3B only as a demo-day upgrade if stable. |
| LLM runtime | ExecuTorch + Qualcomm QNN backend | Export to `.pte` BEFORE the event. Runs on Hexagon NPU. |
| ASR | Whisper (small/base/tiny) | Via ExecuTorch if time allows; whisper.cpp as reliable fallback. See §5. |
| Output | Strict JSON schema | See §4. |
| Storage | Local only | No cloud. |

---

## 3. The two hard problems and their solutions

### Speaker attribution — solved with UX, not ML
Do NOT attempt diarization. Build a **lawyer-controlled capture UI**: a simple toggle/two-button control where the lawyer taps to mark who is speaking (client vs lawyer). Tag each transcript segment with the active speaker. This is more reliable than any on-device diarizer and near-zero engineering.

### Bad transcript poisoning downstream — contained, not eliminated
- **Evidence-linked facts:** every fact shows its source quote, so a wrong fact is spottable against the transcript.
- **Flag low-confidence transcript spans** visually.
- **Fallbacks:** allow typed input and support a prerecorded audio clip for the demo, so a live ASR stumble never kills the demo.

---

## 4. The model layer — NO TRAINING REQUIRED

This is the most important conceptual section. The app needs zero fine-tuning. All accuracy comes from (a) a strict system prompt, (b) a fixed JSON schema, (c) human-authored matter-type checklists injected at runtime.

### Why no training
The task is extraction + reformatting + set-difference, not knowledge recall. The transcript is in the prompt. The legal scaffolding is in the prompt (as checklists). The model just reads provided text and reorganizes it. A base instruction-tuned Llama 3.2 1B does this well. Fine-tuning would need training data we don't have, add a fragile step before quantization/export, and produce no visible demo benefit.

### How "missing information" works without the model knowing law
The legal knowledge lives in **data, not weights**. When the lawyer picks a matter type at session start, the app injects the matching checklist of standard intake elements into the prompt. The model's job is then pure comparison: "which items on this checklist were not covered in the transcript?" That is set-difference over provided text, which a small model does reliably. The app's legal competence equals the quality of these human-authored checklists.

### Matter-type checklists (author 3–4 for the demo; these are data files)

```json
{
  "contract_dispute": [
    "Parties to the agreement identified",
    "Whether the agreement was written or oral",
    "Date the agreement was made",
    "What each party promised (consideration)",
    "What specifically went wrong (the breach)",
    "Dates of the breach / non-performance",
    "Financial loss or damages claimed",
    "Whether the client tried to resolve it / mitigate",
    "Any documents or evidence (invoices, emails, signed copy)",
    "Any witnesses"
  ],
  "landlord_tenant": [
    "Parties (landlord, tenant) identified",
    "Property address",
    "Lease terms (rent, duration, written or oral)",
    "The specific issue (eviction, deposit, repairs, etc.)",
    "Relevant dates (notice given, incident, lease start/end)",
    "Communications between parties",
    "Photos or documents of the issue",
    "Amount of money in dispute"
  ],
  "employment": [
    "Employer and employee identified",
    "Role and employment dates",
    "Nature of the issue (termination, wages, discrimination, etc.)",
    "Key dates (incident, termination, complaint filed)",
    "Whether there is a written contract or handbook",
    "Witnesses or documentation",
    "Whether any complaint was already raised internally",
    "Financial impact"
  ]
}
```

> Public legal intake checklists are a fine starting point for authoring these. Keep each list short (8–12 items). If a real lawyer is available, a 10-minute review massively improves credibility.

### System prompt (starting point — iterate against the demo transcript)

```
You are an intake assistant for a lawyer's first client consultation.
You will be given (1) a checklist of standard intake elements for this matter
type, and (2) a transcript of the consultation with speaker labels.

Your job is ONLY to organize what was said. You must NOT state the law, cite
statutes, give legal advice, or draw legal conclusions.

Produce a single JSON object with exactly these keys:

"facts": an array of objects, each { "statement": "...", "source_quote": "..." }.
  - statement: a fact the CLIENT stated, in plain language.
  - source_quote: the verbatim words from the transcript that support it.
  - Only include facts actually stated. Do not infer, assume, or add anything.

"missing": an array of strings.
  - Go through the provided checklist. List every checklist item that was NOT
    covered anywhere in the transcript. This is a comparison against the
    checklist only — do not invent items beyond it.

"prompts": an array of strings.
  - Short, neutral follow-up questions the lawyer might ask, phrased as
    interview prompts ("Ask about ...") not conclusions ("This is a ...").

Output ONLY the JSON object. No preamble, no markdown, no explanation.
```

### Output schema
```json
{
  "facts":   [ { "statement": "string", "source_quote": "string" } ],
  "missing": [ "string" ],
  "prompts": [ "string" ]
}
```
Parse defensively: strip any ```json fences before parsing, wrap in try/catch.

---

## 5. ExecuTorch usage and the ASR routing decision

### LLM (the non-negotiable ExecuTorch surface)
1. **Before the event:** export Llama 3.2 1B to a `.pte` targeting QNN. The export uses ExecuTorch's llama export path with the `--qnn` flag and 16a4w quantization. This can take 1–2 hours and is gated by upload speed, and Llama weights can't be redistributed, so each team exports their own. DO THIS AHEAD OF TIME.
2. Push `.pte` + tokenizer to the device (`adb push ... /data/local/tmp/...`).
3. App loads the `.pte` via the ExecuTorch Android runtime (`.aar`) and runs inference on the NPU through QNN.

The trickiest integration cost is building the ExecuTorch Android `.aar`, wiring it into the app, and managing on-device memory — not the export command itself. Budget time for this.

### ASR routing — decide on purpose
- **Best for scoring:** export Whisper to `.pte` via ExecuTorch too, so ASR also runs on the NPU. Both stages on ExecuTorch = strongest 40% story.
- **Pragmatic fallback:** whisper.cpp. Reliable and easy, but CPU-bound and NOT ExecuTorch.
- **Plan:** LLM-on-ExecuTorch is the anchor (mandatory). Whisper-on-ExecuTorch is upside; fall back to whisper.cpp if its export fights you. Either way, ensure the LLM stage is unambiguously on QNN/NPU so the project legitimately reads as an ExecuTorch project.

---

## 6. Making "offline" visible (required for the privacy score)
Airplane mode proves nothing a judge can see. Build a small always-on **privacy panel**:
```
Network permission:    disabled
Bytes sent this session: 0
Audio storage:         local only
Transcript storage:    local only
Cloud APIs called:     none
```

## 7. Live metrics panel (required for the technical score)
Make NPU usage visible as real UI: ASR latency, LLM tokens/sec, model load time, backend label (shows the QNN/NPU/ExecuTorch path), and a battery/thermal note.

## 8. Consent screen (one clean screen, in scope)
```
This tool records and transcribes locally on this device.
No audio or transcript is sent to the cloud.
Do you consent to recording for note-taking?   [ Decline ]  [ I consent ]
```

---

## 9. Build order (this sequencing is the plan — follow it)

**Phase 0 — before/at start (do not skip):**
- Export model `.pte` files ahead of time (§5).
- Build a **fake deterministic notes generator**: a function that takes a fixed sample transcript and returns a fixed sample `{facts, missing, prompts}` JSON. This lets the whole app exist before any real model is wired in.
- Prepare the scripted demo audio + 2 scenarios.

**Phase 1 — full app loop, FAKE model:**
Consent screen → start session → pick matter type → capture with speaker-tap UI → call the fake generator → render the three output blocks → save locally. The complete product loop must work end-to-end before any real inference.

**Phase 2 — real local inference:**
Swap the fake generator for the real LLM (ExecuTorch/QNN). Then wire ASR. Then the metrics panel. End state: works offline with at least one real on-device model.

**Phase 3 — prove NPU + privacy:**
Real live metrics panel, the "bytes sent: 0" privacy panel, evidence-linked source quotes on facts, low-confidence span flagging, tune the prompt + JSON schema until notes are reliably clean.

**Phase 4 — polish + package:**
Public GitHub repo, README (setup, run instructions, open-source license, the NOT-building roadmap, known limitations, privacy explanation), scripted demo, screenshots.

---

## 10. Demo flow (what the app must support)
1. Privacy panel visible (bytes sent: 0); airplane mode on.
2. One-tap consent → start "contract dispute" session.
3. Play scripted client story WITH a deliberate wrinkle: facts out of order, "last Friday" said once and "March 8th" later, unsure if the agreement was signed or only emailed, mentions a missing invoice. Tap speaker turns as it plays.
4. Transcript appears live, low-confidence spans flagged; metrics update (latency, tokens/sec).
5. Output renders in three clearly separated blocks. Lead the eye to **Missing Information**.
6. Export locally.

---

## 11. Known limitations to handle gracefully (and name in README)
- **Context window:** a full consultation overflows a 1B model's context. Keep demo sessions short; add simple running summarization only if time allows. Don't over-build.
- **ASR errors:** mitigated by evidence-linking + confidence flags + typed/prerecorded fallback, not eliminated.
- **Checklist coverage:** the app is only as smart as the authored checklists. That's intentional and controllable.
- **On-device ≠ fully secure:** local storage isn't hardened in the MVP. Roadmap, not MVP.

---

## 12. Definition of done (MVP)
- [ ] App runs fully offline on the S25 Ultra.
- [ ] Records audio, transcribes on-device, tags speaker via tap UI.
- [ ] LLM runs on the NPU via ExecuTorch + QNN, producing valid `{facts, missing, prompts}` JSON.
- [ ] Facts show source quotes; missing list reflects the matter-type checklist; prompts stay non-advisory.
- [ ] Privacy panel + live metrics panel visible.
- [ ] Consent screen + local export working.
- [ ] Public repo with README, license, run instructions.
- [ ] Demo script runs clean end-to-end with the scripted scenario.

---

*Design note for whoever builds this: the single biggest risk is spending time on the exciting "AI understands law" half instead of making the boring "notes are reliably excellent + offline is visibly proven" half airtight. Protect the core. Cut anything that sounds like "the model knows the law."*
