package com.subrosa.app.data.notes

import com.subrosa.app.domain.NotesGenerator
import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.NotesResult
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.llm.TextEngine

/**
 * Real on-device generator: PromptAssembler → [LlmEngine] (ExecuTorch) → [NotesJsonParser]. Behind the
 * `GeneratorMode.EXECUTORCH` toggle. Never throws on model garbage or a missing/failed model — every
 * failure becomes a [NotesResult.Failed] so the Results screen degrades gracefully (the demo insurance).
 */
class ExecuTorchNotesGenerator(
    private val engine: TextEngine,
    private val promptStyle: PromptStyle = PromptStyle.CHATML,
    private val assembler: PromptAssembler = PromptAssembler(),
    private val parser: NotesJsonParser = NotesJsonParser(),
    private val seqLen: Int = 2048, // = model context; with thinking off the model stops at EOS well before this
) : NotesGenerator {

    override suspend fun generate(transcript: Transcript, checklist: Checklist): NotesResult {
        if (!engine.isLoaded) {
            runCatching { engine.load() }.getOrElse {
                return NotesResult.Failed(rawOutput = "", reason = "model failed to load: ${it.message}")
            }
        }
        val prompt = assembler.forStyle(transcript, checklist, promptStyle)
        val generated = runCatching { engine.generate(prompt, seqLen) {} }
            .getOrElse { return NotesResult.Failed(rawOutput = "", reason = "generation failed: ${it.message}") }
        // The CPU prompt seeds the answer with JSON_PREFILL (echo=false drops it, so prepend it back); the
        // NPU runner re-templates a raw prompt and emits the whole JSON, so prepend nothing.
        val raw = if (engine.seedsAssistantTurn) PromptAssembler.JSON_PREFILL + generated else generated
        // Pass the checklist so "missing" is computed in code (set-difference over reported coverage).
        return parser.verifyQuotes(parser.parse(raw, checklist), transcript.plainText)
    }
}
