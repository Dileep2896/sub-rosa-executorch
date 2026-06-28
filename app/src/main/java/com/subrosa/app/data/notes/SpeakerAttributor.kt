package com.subrosa.app.data.notes

import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.TranscriptSegment
import com.subrosa.app.llm.TextEngine

/**
 * Content-based speaker attribution. Acoustic diarization proved unreliable on similar voices captured
 * through a single mic, so instead we read the CONVERSATION itself: in a first meeting the lawyer guides
 * and mostly asks questions, while the client describes their situation and answers. A question-word
 * heuristic gives an instant per-line label live; at Stop the LLM does one authoritative pass over the
 * whole transcript. The heuristic is always the fallback, so attribution never breaks the pipeline even
 * if the model output is unparseable.
 */
class SpeakerAttributor(
    private val engine: TextEngine,
    private val promptStyle: PromptStyle,
    private val assembler: PromptAssembler = PromptAssembler(),
) {
    /** Instant best-effort label for one line (used live + as the parse fallback): lawyer if it reads as a question. */
    fun heuristic(text: String): Speaker = if (looksLikeLawyer(text)) Speaker.LAWYER else Speaker.CLIENT

    /**
     * One LLM pass labelling every line LAWYER/CLIENT from content, returned in segment order. Falls back to
     * the per-line heuristic on any failure, unparseable output, or count mismatch — so it is always safe.
     */
    suspend fun attribute(segments: List<TranscriptSegment>): List<Speaker> {
        if (segments.isEmpty()) return emptyList()
        val fallback = segments.map { heuristic(it.text) }
        return runCatching {
            engine.load()
            val prompt = assembler.assembleSpeakerAttribution(segments, promptStyle)
            // CPU seeds the assistant turn with "[" (echo=false drops it) so prepend it back; the NPU runner
            // emits the whole array, so prepend nothing.
            val raw = (if (engine.seedsAssistantTurn) "[" else "") + engine.generate(prompt, seqLen = SEQ_LEN) {}
            parseLabels(raw, segments.size) ?: fallback
        }.getOrDefault(fallback)
    }

    /** Pull the first N L/C labels (in order) from the model's array; null if fewer than N were found. */
    private fun parseLabels(raw: String, n: Int): List<Speaker>? {
        val body = raw.substringAfter('[').substringBefore(']')
        val tokens = Regex("[LClc]").findAll(body).map { it.value.uppercase() }.toList()
        if (tokens.size < n) return null
        return (0 until n).map { if (tokens[it] == "L") Speaker.LAWYER else Speaker.CLIENT }
    }

    private companion object {
        const val SEQ_LEN = 2048
        val QUESTION_WORDS = setOf(
            "what", "when", "where", "who", "whom", "whose", "why", "how", "which",
            "did", "do", "does", "is", "are", "was", "were", "can", "could", "would", "will",
            "have", "has", "had", "tell", "describe", "explain", "walk", "let",
        )

        fun looksLikeLawyer(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return false
            if (t.endsWith("?")) return true
            val first = t.split(Regex("\\s+")).firstOrNull()
                ?.lowercase()?.trim('.', ',', '"', '\'', '“', '”') ?: return false
            return first in QUESTION_WORDS
        }
    }
}
