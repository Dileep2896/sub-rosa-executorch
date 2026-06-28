# Sub Rosa — Demo Script & Runbook

A ~3-minute live demo that shows the whole product: a real two-voice consultation captured and
transcribed on-device, an on-device LLM that produces a verified intake scorecard, the privacy
proof, and a one-tap lawyerly PDF — all with nothing leaving the phone.

> **The three things judges must remember:** (1) the AI runs **on the NPU, offline**; (2) it's
> **private by construction** (no internet permission, airplane mode on); (3) it **catches what the
> lawyer forgot to ask** — the missing-info scorecard.

---

## 0 · Pre-flight (do this before you walk up)

- **Device:** S25 Ultra, cool (not just off a charge run), ≥ 50% battery, brightness up.
- **Airplane mode ON**, Wi-Fi + Bluetooth off. (This is a live privacy proof — leave it on the whole demo.)
- App installed; open it once and **warm the model** (flip ENGINE → EXECUTORCH; wait for the first
  load so processing is instant on stage).
- **Engine = EXECUTORCH** (NPU), **Transcriber = WHISPER**. Know where the FAKE / SCRIPTED toggles are.
- Consultation audio ready to play (or two people to read Appendix A). Lapel volume tested.
- **Backups:** a recorded golden-run video on the device, and the FAKE/SCRIPTED toggles as the live net.

---

## 1 · Hook + privacy proof (~20s)

> "This is **Sub Rosa** — a private note-taker for a lawyer's first meeting with a new client.
> Everything you're about to see runs **on this phone**. No servers, no cloud. In fact —" *(open the
> PROOF panel)* "— the app doesn't even hold the internet permission. **Internet: not declared.
> Outbound socket: blocked by the OS. Zero bytes in, zero bytes out.** And we're in airplane mode."

*Beat. Let them read the panel.* This is pillar #2, stated in the first 20 seconds.

---

## 2 · The consultation — two voices (~75s)

> "Maya owns a bakery. She hired a web studio that took her deposit and ghosted her. This is her
> first meeting with her lawyer. I'll tap to mark who's speaking as they talk."

Play the recording (or have two people read **Appendix A**). On screen: the live transcript appears
line by line; the lawyer taps the **speaker plate** to switch CLIENT/LAWYER; one low-confidence line
shows the amber flag (tap to correct). Let it run — the content is doing the work.

*Do not narrate over the whole thing — let ~45s of it breathe so the live transcription lands.*

---

## 3 · Seal it → on-device inference (~20s)

> "I'll **seal the record**. Now a **Llama model runs on the phone's NPU** to turn that conversation
> into structured notes — still offline."

*(Processing screen.)* Point at the metrics: **BACKEND: Hexagon NPU · QNN**, **N tokens/sec**,
model-load + generation time. (On the dev emulator this reads CPU/XNNPACK — on the S25 it's the NPU.)

> "This is the part that normally goes to OpenAI or a server. Here it's the Snapdragon NPU. Watch the
> tokens-per-second." *(Optional A/B: flip ENGINE NPU↔CPU to show the NPU is faster — pillar #1.)*

---

## 4 · The payoff — the missing-info scorecard (~35s)

> "Here's the headline. The app took everything Maya said and compared it against a **standard
> contract-intake checklist** — and it flags **what she didn't cover.**"

Point at the scorecard: **"N of 10 intake items not covered"** — e.g. *witnesses*, *mitigation*.

> "That's the thing a tired lawyer at 6pm forgets to ask. The app never forgets. And this list is
> **computed in code** from the checklist — the model can't invent or miss an item."

Then the trust layer:

> "Every fact links to **Maya's exact words** —" *(tap a fact → the verbatim quote highlights)* "—
> so nothing is hallucinated. And it caught an **inconsistency**: she said the deadline was *'last
> Friday'* and later *'the eighth.'* The app surfaces the hedge instead of papering over it."

That's pillar #3 + the safety story in one breath.

---

## 5 · Rapid tour of the rest (~40s)

Quick, confident hops — one line each:

- **Resume / gaps close:** "The follow-ups it suggests? Ask them in the *same* session, re-run, and
  watch the gaps shrink — **2 of 10 → 0 of 10.**"
- **PDF report:** *(Case → EXPORT REPORT)* "One tap → a **lawyerly PDF** — privileged-&-confidential
  header, the facts with their quotes, the outstanding items — generated **on-device**, share or save."
- **Privacy at rest:** "Everything's **AES-256 encrypted** on the phone and locked behind
  **biometrics**. Re-check the panel — **still zero bytes.**"
- **Organization:** "It's organized the way a firm thinks — **client → case → consultations &
  documents.**"

---

## 6 · Close (~10s)

> "On-device AI on the NPU, attorney-client privilege preserved by construction, and a checklist that
> never forgets a question. Private, fast, and it makes the lawyer better. That's **Sub Rosa**."

**Total: ~3:00.** If you have only 90s: do §1 → §2 (30s of it) → §4 → one line of §5 (PDF) → §6.

---

## Appendix A · Consultation dialogue (for recording / the two voices)

*Matter: contract dispute. **L** = Lawyer (calm, professional). **M** = Maya, the client (warm, a bit
frustrated). Designed so she covers the agreement, deposit, breach and damages, but **never the
witnesses or her mitigation steps** (those become the "missing" items), and includes two wrinkles:
a **date contradiction** and a **signed-vs-emailed hedge**.*

1. **L:** Thanks for coming in, Maya. Tell me what's been going on.
2. **M:** So I hired a company called Trellis Studio to build a website for my bakery, and they never finished it.
3. **L:** Okay. What did the two of you agree to?
4. **M:** We agreed they'd deliver the finished website for four thousand dollars, and I paid two thousand up front as a deposit.
5. **L:** When did you first agree to the work?
6. **M:** It was back in March — March 8th, I think. They were supposed to have it done by last Friday, but they just stopped responding to me.
7. **L:** Did you sign a contract?
8. **M:** Honestly, I'm not sure if we ever signed an actual contract, or if it was all just over email.
9. **L:** And you said the deadline was last Friday?
10. **M:** Well — the deadline, I keep second-guessing myself, but I think they actually promised it by the eighth.
11. **L:** Do you have the paperwork — the agreement, the invoice for your deposit?
12. **M:** I've got all our emails saved, but I cannot find the invoice for my deposit anywhere. That two thousand dollars is just gone, and now I'll have to pay someone else to finish the site.
13. **L:** Okay. Let me make sure I understand the timeline and what you have in writing.

*(~75–85s spoken. This mirrors the app's built-in fixture so the SCRIPTED fallback matches the audio.)*

---

## Appendix B · Feature → moment map

| Feature | Shown at | Scored pillar |
|---|---|---|
| On-device LLM on NPU (ExecuTorch + QNN), tokens/sec, NPU↔CPU A/B | §3 | **NPU proof** |
| On-device Whisper ASR (real speech → transcript) | §2 | NPU proof (ASR upside) |
| No-internet permission + outbound-socket-blocked probe + 0 bytes + airplane mode | §1, §5 | **Privacy proof** |
| Missing-info scorecard (code-computed set-difference) | §4 | **Missing-info scorecard** |
| Fact → verbatim source quote (hallucination guard) | §4 | (trust) |
| Inconsistency catch (date contradiction / signed-vs-emailed) | §4 | (trust) |
| Speaker-tap capture, live transcript, low-confidence flag | §2 | (UX) |
| Living session: ask follow-ups, re-run, gaps close 2/10→0/10 | §5 | (UX) |
| Themed PDF case report, on-device | §5 | (polish) |
| AES-256 at rest + biometric lock + client→case→docs | §5 | (privacy/UX) |

---

## Appendix C · Failure pivots (say these, don't freeze)

- **NPU silently falls back to CPU** → "It's running on the CPU fallback right now — the NPU build is
  verified offline; what you're seeing live is the slower path and it's still instant."
- **ASR garbles a word** → tap to correct it on the spot ("and the lawyer fixes it in one tap"), or
  flip Transcriber → SCRIPTED.
- **Model output looks off** → flip ENGINE → FAKE ("here's the deterministic reference run") and move on.
- **Anything hard-locks** → play the golden-run video; keep narrating.
- **Never** claim NPU while on CPU unflagged, never claim 0 bytes without the panel showing it.

---

## Appendix D · The recorded audio

`brand/demo-consultation.wav` — the two-voice consultation (16 kHz mono, whisper-ready). Generated
from Appendix A. Re-record with real actors for the final cut if you want; keep it 16 kHz mono so the
on-device ASR ingests it directly.
