# Sub Rosa — Architecture

On-device AI legal-intake note-taker for the **Qualcomm × Meta ExecuTorch Hackathon**.
Records a lawyer's first client consultation → transcribes (Whisper) → diarizes speakers (pyannote) →
extracts structured intake notes on the **Hexagon NPU** — **100% on-device, fully offline**.

> **Living document.** These diagrams reflect the current build. Update the relevant diagram whenever the
> architecture changes. Rendered by GitHub, VS Code, `mermaid.live`, and most slide tools.
>
> _Last updated: 2026-06-28_

---

## 1. System overview — the on-device pipeline

```mermaid
flowchart LR
    subgraph DEVICE["📱 On-device only · Samsung Galaxy S25 Ultra · Snapdragon 8 Elite"]
        direction TB
        MIC["🎙️ Microphone"]
        MIC --> ASR["<b>Whisper base.en</b><br/>whisper.cpp · CPU/NEON<br/><i>speech → text lines</i>"]
        ASR --> TRX["Transcript lines<br/><i>live, with timestamps</i>"]
        TRX --> ATTR["<b>Speaker diarization</b><br/>pyannote · sherpa-onnx<br/><i>voiceprints · who-spoke-when</i>"]
        ATTR --> LAB["Speaker-labeled transcript"]
        LAB --> LLM["<b>Qwen3-1.7B</b><br/>ExecuTorch · <b>QNN → Hexagon NPU</b><br/><i>extract → strict JSON</i>"]
        LLM --> NOTES["<b>Intake notes</b><br/>facts · missing items · follow-ups"]
        NOTES --> STORE["🔒 Encrypted local store<br/>+ themed PDF case report"]
    end
    CPU(["CPU · XNNPACK<br/><i>fallback only</i>"])
    LLM -. "on any DSP failure" .-> CPU
    NET(["☁️ Internet / Cloud"])
    DEVICE -. "🚫 no INTERNET permission · OS-enforced" .-> NET
    style NET stroke-dasharray: 5 5
    style CPU stroke-dasharray: 4 4
```

The LLM runs on the **Hexagon NPU** through ExecuTorch's QNN backend. CPU/XNNPACK is a transparent
safety net, not the main path: `FallbackTextEngine` flips to it (and shows a red `CPU FALLBACK` chip)
only if the DSP fails mid-run.

---

## 2. Layered architecture (Android app)

Single Gradle module, Jetpack Compose, single Activity, **manual DI** (`AppContainer`), MVVM with
**enum-driven phase navigation** (no Nav-Compose).

```mermaid
flowchart TB
    subgraph L1["🖼️ UI · Jetpack Compose"]
        A1["SubRosaApp"]
        A2["Screens: Home · Client · Case · Consent<br/>Capture · Processing · Results"]
    end
    subgraph L2["🧠 Presentation · MVVM"]
        B1["SessionViewModel<br/>SessionUiState · StateFlow"]
    end
    subgraph L3["🧩 AppContainer · manual DI"]
        C1["wires engines · stores · metrics · privacy"]
    end
    subgraph L4["📐 Domain · pure Kotlin"]
        D1["Interfaces: NotesGenerator · Transcriber · TextEngine"]
        D2["Models: Notes · NotesResult · Transcript<br/>Speaker · Case · Client · Session"]
    end
    subgraph L5["⚙️ Data · engines & stores"]
        E1["ASR: AudioRecorder · WhisperEngine · WhisperTranscriber"]
        E2["Diarization: OnnxDiarizer · LiveSpeakerId<br/>(pyannote · sherpa-onnx)"]
        E3["Notes: PromptAssembler · NpuRunnerEngine / LlmEngine<br/>NotesJsonParser · ExecuTorchNotesGenerator"]
        E4["Persistence: Client / Case / Session / Document stores<br/>AssetChecklistRepository"]
        E5["Report: CaseReport · CaseReportPdf"]
    end
    subgraph L6["🔌 Native runtimes (on device)"]
        F1["libwhisper.so<br/>whisper.cpp + ggml · CPU"]
        F2["libsherpa-onnx.so<br/>ONNX Runtime · pyannote"]
        F3["ExecuTorch QNN AAR + qnn_llama_runner<br/>Hexagon NPU (XNNPACK CPU fallback)"]
    end

    L1 --> L2 --> L3
    L3 --> L4
    L3 --> L5
    E1 --> F1
    E2 --> F2
    E3 --> F3
```

---

## 3. Consultation flow (phase navigation)

```mermaid
stateDiagram-v2
    [*] --> HOME
    HOME --> NEW_CLIENT: new client
    HOME --> CLIENT_PROFILE: open client
    HOME --> RESULTS: guided demo
    NEW_CLIENT --> CLIENT_PROFILE
    CLIENT_PROFILE --> NEW_CASE: new case
    CLIENT_PROFILE --> CASE_PROFILE: open case
    NEW_CASE --> CASE_PROFILE
    CASE_PROFILE --> CONSENT: begin consultation
    CONSENT --> CAPTURE: I consent
    CAPTURE --> PROCESSING: Stop and seal
    PROCESSING --> RESULTS: notes ready
    RESULTS --> CAPTURE: continue consultation
    RESULTS --> CASE_PROFILE: seal and finish
    CASE_PROFILE --> [*]
```

---

## 4. Live capture & pyannote speaker diarization

Whisper streams the mic into committed lines with sample-offset timestamps. During capture,
`LiveSpeakerId` (sherpa-onnx voice embeddings) assigns each segment to a voice in real time. At
**Stop**, the full `OnnxDiarizer` pass (pyannote segmentation-3.0 + wespeaker embeddings + fast
clustering) refines every line, seeded by the **"people in the room"** count. The lawyer's manual
speaker tap always overrides, so the demo can never break.

```mermaid
sequenceDiagram
    autonumber
    participant Mic as 🎙️ AudioRecorder
    participant VM as SessionViewModel
    participant W as WhisperTranscriber
    participant L as LiveSpeakerId
    participant D as OnnxDiarizer
    participant UI as Transcript UI

    Mic->>VM: PCM chunks · 16 kHz mono
    VM->>W: stream audio
    loop every ~2.8 s window
        W-->>VM: SegmentFinal · text + startMs..endMs + float audio
        VM->>L: embed segment → assign voice (cosine ≥ 0.7)
        VM->>UI: append animated line · speaker plate
    end
    Note over VM,D: Stop & seal
    VM->>D: diarize(full PCM, numSpeakers)
    D->>D: pyannote segmentation + embeddings + clustering
    D-->>VM: who-spoke-when segments
    VM->>UI: relabel every line → notes pass
```

> **Design note.** Acoustic diarization runs as a **post-Stop pass** (full pyannote is ~0.26× of audio
> duration — too slow to re-run continuously), with live voiceprints giving an instant in-capture split.
> Models are pushed to the device (`diarize-segmentation.onnx`, `diarize-embedding.onnx`), never committed.

---

## 5. Notes generation (LLM → strict JSON)

Legal competence lives in **human-authored checklists + the system prompt**, not in the weights — the
model only extracts and reformats, and the app computes the missing items by set-difference.

```mermaid
flowchart TB
    T["Speaker-labeled transcript"] --> G{"Edge-case guard<br/>enough words?"}
    G -- "no" --> INS["NotesResult.Insufficient<br/>no report fabricated"]
    G -- "yes" --> PA["PromptAssembler<br/>system + checklist + transcript<br/>ChatML · no-think · JSON prefill"]
    PA --> LLM["NpuRunnerEngine · Qwen3-1.7B<br/><b>ExecuTorch · QNN → Hexagon NPU</b>"]
    LLM -. "DSP failure" .-> CPU["LlmEngine · CPU/XNNPACK<br/><i>fallback</i>"]
    LLM --> P["NotesJsonParser<br/>brace-match · field salvage · quote guard"]
    CPU --> P
    CK["Intake checklist<br/>human-authored"] -. "covered ids → set-difference → missing" .-> P
    P --> R{"NotesResult"}
    R --> PAR["Parsed"]
    R --> PRT["Partial · repaired"]
    R --> FL["Failed · degrade gracefully"]
    PAR --> MRG["merge w/ prior report<br/>if continuing a consultation"]
    PRT --> MRG
    MRG --> UIS["Results screen<br/>facts + verbatim quotes<br/>missing-items scorecard · follow-ups"]
```

---

## 6. On-device models & runtimes

```mermaid
flowchart TB
    subgraph ASR["🗣️ ASR"]
        M1["whisper base.en<br/>~142 MB ggml"] --> R1["whisper.cpp + ggml<br/>CPU · NEON · 6 threads · -O3<br/>16 KB page-aligned .so"]
    end
    subgraph ATTRIB["👥 Diarization (pyannote)"]
        M2["segmentation-3.0 (~6 MB)<br/>+ wespeaker embedding (~26 MB)"] --> R2["sherpa-onnx · ONNX Runtime · CPU<br/>OnnxDiarizer + LiveSpeakerId"]
    end
    subgraph GEN["📝 Notes LLM"]
        M3["Qwen3-1.7B QNN<br/>model-qnn.pte · ~1.75 GB"] --> R3["<b>ExecuTorch QNN → Hexagon NPU</b><br/>qnn_llama_runner · ~40 tok/s<br/>XNNPACK CPU = fallback"]
    end
    NOTE["Models pushed to the app's external files dir · NEVER committed to git<br/>LLM primary = Hexagon NPU (model-qnn.pte + HTP libs); CPU model.pte is the safety net"]
    ASR -.- NOTE
    ATTRIB -.- NOTE
    GEN -.- NOTE
```

---

## 7. Privacy & trust model

```mermaid
flowchart LR
    subgraph APP["Sub Rosa · app sandbox"]
        DATA["Consultations · transcripts · notes"]
        ENC["🔒 AES-256-GCM<br/>Android Keystore"]
        BIO["🔐 Biometric lock<br/>FragmentActivity"]
        BIO --> DATA
        DATA --> ENC
    end
    MAN["AndroidManifest<br/><b>no INTERNET permission</b>"]
    PROBE["PrivacyMonitor<br/>live outbound-socket probe + byte counter"]
    NETX(["🚫 Network socket"])
    MAN --> APP
    PROBE --> APP
    APP -. "connect 8.8.8.8:53 → EACCES (blocked by OS)" .-> NETX
    style NETX stroke-dasharray: 5 5
```

---

## 8. Data & persistence

```mermaid
erDiagram
    CLIENT ||--o{ CASE : has
    CASE ||--o{ SESSION : has
    CASE ||--o{ DOCUMENT : has
    SESSION ||--o| NOTES : produces
    NOTES ||--o{ FACT : contains

    CLIENT {
        string id
        string name
        string contact
    }
    CASE {
        string id
        string clientId
        MatterType type
        string title
    }
    SESSION {
        string id
        string caseId
        Status status
        Transcript transcript
    }
    NOTES {
        list facts
        list missing
        list prompts
    }
    FACT {
        string statement
        string source_quote
        bool verified
    }
    DOCUMENT {
        string id
        string caseId
        string uri
    }
```

> Stores are JSON (`kotlinx.serialization`) in the app's private `filesDir`, encrypted at rest. The
> **case report** aggregates *all* of a case's consultations (combined facts, intersected missing items)
> into a themed PDF — shareable or saved to Downloads as `SubRosa_<Client>_<Case>.pdf`.
