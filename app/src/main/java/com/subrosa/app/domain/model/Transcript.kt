package com.subrosa.app.domain.model

import kotlinx.serialization.Serializable

/** Who is speaking. Attribution comes from the lawyer's tap UI, never from diarization ML. */
enum class Speaker { CLIENT, LAWYER }

/** One attributed span of transcript text, with optional ASR confidence + timing. */
@Serializable
data class TranscriptSegment(
    val speaker: Speaker,
    val text: String,
    /** 0f..1f. Low values are visually flagged in the transcript. Defaults to 1f for typed/scripted input. */
    val confidence: Float = 1f,
    val startMs: Long = 0,
    val endMs: Long = 0,
) {
    val isLowConfidence: Boolean get() = confidence < LOW_CONFIDENCE_THRESHOLD

    companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.6f
    }
}

@Serializable
data class Transcript(val segments: List<TranscriptSegment> = emptyList()) {

    /** Just the client's speech, joined — useful when only the client's statements matter. */
    val clientText: String
        get() = segments.filter { it.speaker == Speaker.CLIENT }.joinToString(" ") { it.text }

    /** Speaker-labeled transcript, one line per segment. This is what goes into the LLM prompt. */
    val labeled: String
        get() = segments.joinToString("\n") { "${it.speaker.name}: ${it.text}" }

    /** All text regardless of speaker — used by the quote-verification guard. */
    val plainText: String
        get() = segments.joinToString(" ") { it.text }
}
