# Sub Rosa — Architecture

On-device AI legal-intake note-taker for the **Qualcomm × Meta ExecuTorch Hackathon**.
Records a lawyer's first client consultation → transcribes → attributes speakers by content → extracts structured
intake notes — **100% on-device, fully offline**.

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
        ASR --> TRX["Transcript lines<br/><i>live: question-heuristic accent</i>"]
        TRX --> ATTR["<b>Speaker attribution</b><br/>Qwen3 LLM · content-based<br/><i>lawyer asks / client narrates</i>"]
        ATTR --> LAB["Speaker-labeled transcript"]
        LAB --> LLM["<b>Qwen3-1.7B</b><br/>ExecuTorch · XNNPACK/CPU<br/><i>extract → strict JSON</i>"]
        LLM --> NOTES["<b>Intake notes</b><br/>facts · missing items · follow-ups"]
        NOTES --> STORE["🔒 Encrypted local store<br/>+ themed PDF case report"]
    end
    NET(["☁️ Internet / Cloud"])
    DEVICE -. "🚫 no INTERNET permission · OS-enforced" .-> NET
    style NET stroke-dasharray: 5 5
```

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
        D1["Interfaces: NotesGenerator · Transcriber"]
        D2["Models: Notes · NotesResult · Transcript<br/>Speaker · Case · Client · Session"]
    end
    subgraph L5["⚙️ Data · engines & stores"]
        E1["ASR: AudioRecorder · WhisperEngine · WhisperTranscriber"]
        E2["Speaker attribution: SpeakerAttributor<br/>(content-based · via the LLM)"]
        E3["Notes: PromptAssembler · LlmEngine<br/>NotesJsonParser · ExecuTorchNotesGenerator"]
        E4["Persistence: Client / Case / Session / Document stores<br/>AssetChecklistRepository"]
        E5["Report: CaseReport · CaseReportPdf"]
    end
    subgraph L6["🔌 Native runtimes (on device)"]
        F1["libwhisper.so<br/>whisper.cpp + ggml · CPU"]
        F2["libsherpa-onnx.so (vendored · dormant)"]
        F3["executorch-android AAR · XNNPACK"]
    end

    L1 --> L2 --> L3
    L3 --> L4
    L3 --> L5
    E1 --> F1
    E2 --> F3
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

## 4. Live capture & content-based speaker attribution

Whisper streams the mic into committed lines; each line gets an **instant question-word heuristic** label
(lawyer asks / client narrates) shown as a brass/oxblood left accent. At **Stop**, the LLM reads the whole
transcript and authoritatively labels every line — content, not voiceprints.

```mermaid
sequenceDiagram
    autonumber
    participant Mic as 🎙️ AudioRecorder
    participant VM as SessionViewModel
    participant W as WhisperTranscriber
    participant S as SpeakerAttributor
    participant UI as Transcript UI

    Mic->>VM: PCM chunks · 16 kHz mono
    VM->>W: stream audio
    loop every ~2.8 s window
        W-->>VM: SegmentFinal · text + startMs..endMs
        VM->>VM: question-heuristic → lawyer / client
        VM->>UI: append animated line · brass/oxblood accent
    end
    Note over VM,S: Stop & seal
    VM->>S: attribute(all lines)
    S->>S: LLM pass → label each line L / C (heuristic fallback)
    S-->>VM: speaker per line
    VM->>UI: relabel → notes pass
```

> **Design note.** Acoustic diarization (pyannote/sherpa-onnx + a greedy voiceprint matcher) was tried
> first but failed on similar voices through one mic. Content attribution — the lawyer drives with
> questions, the client narrates — is reliable for an intake and needs no audio buffer.

---

## 5. Notes generation (LLM → strict JSON)

Legal competence lives in **human-authored checklists + the system prompt**, not in the weights — the
model only extracts, reformats, and the app computes the missing items by set-difference.

```mermaid
flowchart TB
    T["Speaker-labeled transcript<br/>(LLM content-attributed)"] --> G{"Edge-case guard<br/>enough words?"}
    G -- "no" --> INS["NotesResult.Insufficient<br/>no report fabricated"]
    G -- "yes" --> PA["PromptAssembler<br/>system + checklist + transcript<br/>ChatML · no-think · JSON prefill"]
    PA --> LLM["LlmEngine · Qwen3-1.7B<br/>ExecuTorch · CPU/XNNPACK"]
    LLM --> P["NotesJsonParser<br/>brace-match · field salvage · quote guard"]
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
    subgraph ATTRIB["👥 Speaker attribution"]
        M2["question-word heuristic (live)<br/>+ the Notes LLM (at Stop)"] --> R2["content-based · no separate model<br/>sherpa-onnx AAR vendored but dormant"]
    end
    subgraph GEN["📝 Notes LLM"]
        M3["Qwen3-1.7B<br/>~1.2 GB .pte · 8da4w"] --> R3["ExecuTorch 1.3.1 QNN AAR<br/>XNNPACK CPU now · Hexagon NPU-ready"]
    end
    NOTE["Models pushed to the app's external files dir · NEVER committed to git<br/>LLM runs on the ExecuTorch QNN AAR — flip to the Hexagon NPU with a QNN .pte + HTP libs"]
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
    PROBE["Live TrafficStats<br/>byte counter (on-screen proof)"]
    NETX(["🚫 Network socket"])
    MAN --> APP
    PROBE --> APP
    APP -. "cannot open a socket" .-> NETX
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
