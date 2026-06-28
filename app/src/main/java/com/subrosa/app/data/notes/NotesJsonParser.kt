package com.subrosa.app.data.notes

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.Fact
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.NotesResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Turns raw LLM output into a [NotesResult]. NEVER throws — a small model will emit fenced JSON,
 * trailing prose, or dropped fields, and the pipeline must degrade rather than crash.
 *
 * Degradation ladder:
 *   1. [extractJsonObject] — strip ```json fences, then brace-match the first balanced `{...}`
 *      (tolerates leading/trailing prose).
 *   2. strict decode to [Notes] -> [NotesResult.Parsed].
 *   3. field-by-field salvage (pull facts/missing/prompts independently) -> [NotesResult.Partial].
 *   4. otherwise [NotesResult.Failed], keeping the raw text.
 *
 * [verifyQuotes] then enforces the hallucination guard against the transcript.
 */
class NotesJsonParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    },
) {

    fun parse(raw: String, checklist: Checklist? = null): NotesResult {
        val candidate = extractJsonObject(raw)
            ?: return NotesResult.Failed(raw, "no JSON object found in output")

        // Strict decode (fast path) only applies to the legacy model-lists-"missing" shape.
        if (checklist == null) {
            runCatching { json.decodeFromString<Notes>(candidate) }
                .onSuccess { return NotesResult.Parsed(it) }
        }

        // Field-by-field salvage: parse to a JsonObject and pull each block independently.
        val obj: JsonObject = runCatching { json.parseToJsonElement(candidate).jsonObject }
            .getOrNull()
            ?: return NotesResult.Failed(raw, "content was not a JSON object after cleaning")

        val errors = mutableListOf<String>()
        val facts = obj["facts"].decodeFactList(errors)
        val prompts = obj["prompts"].decodeStringList(errors, "prompts")
        // "missing" is COMPUTED, not trusted to the model: when a checklist is supplied and the model
        // reported the ids it COVERED, we take the set-difference (the model can neither invent nor
        // drop checklist items). Falls back to a model-listed "missing" otherwise (fixtures/tests).
        val missing = if (checklist != null && obj["covered"] != null) {
            missingFromCoverage(checklist, obj["covered"].decodeStringList(errors, "covered"))
        } else {
            obj["missing"].decodeStringList(errors, "missing")
        }

        val notes = Notes(facts = facts, missing = missing, prompts = prompts)
        return if (errors.isEmpty()) NotesResult.Parsed(notes)
        else NotesResult.Partial(notes, errors)
    }

    /** Set-difference: the checklist items whose id/label the model did NOT mark as covered. */
    private fun missingFromCoverage(checklist: Checklist, covered: List<String>): List<String> {
        val marks = covered.map { normalize(it) }.filter { it.isNotBlank() }
        return checklist.items
            .filterNot { item ->
                val id = normalize(item.id)
                val label = normalize(item.label)
                marks.any { m -> m == id || m == label || label.contains(m) || m.contains(id) }
            }
            .map { it.label }
    }

    /**
     * Hallucination guard. Returns [result] with every fact whose [Fact.sourceQuote] does not appear
     * (normalized) in [transcriptText] marked `verified = false`. If any fact fails, the result is
     * demoted to [NotesResult.Partial] so the UI can flag it.
     */
    fun verifyQuotes(result: NotesResult, transcriptText: String): NotesResult {
        val notes = result.notesOrNull ?: return result
        val haystack = normalize(transcriptText)
        var anyUnverified = false
        val checkedFacts = notes.facts.map { f ->
            // Small models often copy the speaker-labeled transcript line ("CLIENT: ...") into the
            // statement/quote; strip the label so the quote matches the transcript and reads cleanly.
            val statement = stripSpeakerLabel(f.statement)
            val quote = stripSpeakerLabel(f.sourceQuote)
            val ok = quote.isNotBlank() && haystack.contains(normalize(quote))
            if (!ok) anyUnverified = true
            f.copy(statement = statement, sourceQuote = quote, verified = ok)
        }
        val checked = notes.copy(facts = checkedFacts)
        return when (result) {
            is NotesResult.Failed -> result
            is NotesResult.Insufficient -> result
            is NotesResult.Partial -> NotesResult.Partial(checked, result.errors + unverifiedNote(checkedFacts))
            is NotesResult.Parsed ->
                if (anyUnverified) NotesResult.Partial(checked, listOf(unverifiedNote(checkedFacts)))
                else NotesResult.Parsed(checked)
        }
    }

    private fun unverifiedNote(facts: List<Fact>): String {
        val n = facts.count { !it.verified }
        return "$n fact(s) had a source_quote not found in the transcript (flagged unverified)"
    }

    // ---- helpers ----

    /** Strip code fences, then return the first balanced `{...}` block (string-aware), or null. */
    internal fun extractJsonObject(raw: String): String? {
        val defenced = raw
            .replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
        val start = defenced.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until defenced.length) {
            val c = defenced[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return defenced.substring(start, i + 1)
                    }
                }
            }
        }
        return null // unbalanced braces
    }

    private fun JsonElement?.decodeStringList(errors: MutableList<String>, field: String): List<String> {
        if (this == null) return emptyList()
        val arr = this as? JsonArray
        if (arr == null) {
            errors += "'$field' was not an array"
            return emptyList()
        }
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
    }

    private fun JsonElement?.decodeFactList(errors: MutableList<String>): List<Fact> {
        if (this == null) return emptyList()
        val arr = this as? JsonArray
        if (arr == null) {
            errors += "'facts' was not an array"
            return emptyList()
        }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val statement = (o["statement"] as? JsonPrimitive)?.contentOrNull
            val quote = (o["source_quote"] as? JsonPrimitive)?.contentOrNull
                ?: (o["sourceQuote"] as? JsonPrimitive)?.contentOrNull
            if (statement.isNullOrBlank()) null else Fact(statement = statement, sourceQuote = quote ?: "")
        }
    }

    /** Strip a leading "CLIENT:" / "LAWYER:" speaker label a model may copy into a field. */
    private fun stripSpeakerLabel(s: String): String =
        s.replaceFirst(Regex("^\\s*(client|lawyer)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "").trim()

    /** Lowercase and collapse whitespace/punctuation so substring matching tolerates ASR/formatting noise. */
    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[\\s\\p{Punct}]+"), " ").trim()
}
