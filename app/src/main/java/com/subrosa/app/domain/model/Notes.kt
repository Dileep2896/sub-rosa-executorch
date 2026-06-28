package com.subrosa.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single fact the client stated, paired with the verbatim transcript text supporting it. */
@Serializable
data class Fact(
    val statement: String,
    @SerialName("source_quote") val sourceQuote: String,
    /**
     * True once we've confirmed [sourceQuote] actually appears in the transcript. The hallucination
     * guard ([com.subrosa.app.data.notes.NotesJsonParser.verifyQuotes]) sets this to false for any
     * fact whose quote can't be found — such facts are shown greyed-out rather than as citations.
     */
    val verified: Boolean = true,
)

/** The structured intake notes: the three output blocks the model produces. */
@Serializable
data class Notes(
    val facts: List<Fact> = emptyList(),
    val missing: List<String> = emptyList(),
    val prompts: List<String> = emptyList(),
)

/**
 * Outcome of turning raw model output into [Notes]. Defensive parsing is a RETURN TYPE, not a hope:
 * a 1B model will sometimes emit fenced / garbage / partial JSON, and the UI must degrade gracefully
 * instead of throwing.
 */
sealed interface NotesResult {
    /** Fully parsed and schema-valid. */
    data class Parsed(val notes: Notes) : NotesResult

    /** Some blocks were salvaged; [errors] explains what was dropped, defaulted, or flagged. */
    data class Partial(val notes: Notes, val errors: List<String>) : NotesResult

    /** Could not extract usable JSON at all. [rawOutput] is kept for display + on-device debugging. */
    data class Failed(val rawOutput: String, val reason: String) : NotesResult

    /**
     * The consultation didn't capture enough to summarize (e.g. the lawyer-only, or only a few words).
     * We deliberately do NOT run the model here — a report is never fabricated from thin input.
     */
    data class Insufficient(val reason: String) : NotesResult

    /** The notes if any were recovered, else null. */
    val notesOrNull: Notes?
        get() = when (this) {
            is Parsed -> notes
            is Partial -> notes
            is Failed -> null
            is Insufficient -> null
        }
}
