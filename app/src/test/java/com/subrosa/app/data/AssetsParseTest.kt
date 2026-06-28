package com.subrosa.app.data

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.Transcript
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the hand-authored asset JSON against the domain models WITHOUT a device. Unit tests run
 * with the module dir as the working directory, so assets are read straight off disk.
 */
class AssetsParseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun asset(name: String) = File("src/main/assets/$name").readText()
    private fun normalize(s: String) =
        s.lowercase().replace(Regex("[\\s\\p{Punct}]+"), " ").trim()

    @Test fun checklistsParseAndCoverDemoMatter() {
        val lists = json.decodeFromString<List<Checklist>>(asset("checklists.json"))
        assertTrue(lists.any { it.matterType == MatterType.CONTRACT_DISPUTE })
        assertTrue("every checklist must have items", lists.all { it.items.isNotEmpty() })
    }

    @Test fun sampleTranscriptParses() {
        val t = json.decodeFromString<Transcript>(asset("sample_transcript.json"))
        assertTrue(t.segments.size >= 5)
        assertTrue("demo needs a low-confidence span to flag", t.segments.any { it.isLowConfidence })
    }

    @Test fun fakeNotesParse() {
        val n = json.decodeFromString<Notes>(asset("fake_notes.json"))
        assertTrue(n.facts.isNotEmpty())
        assertTrue(n.missing.isNotEmpty())
        assertTrue(n.prompts.isNotEmpty())
    }

    /** The invariant that keeps the demo's quote-guard green: every fake fact is verbatim-grounded. */
    @Test fun everyFakeFactQuoteAppearsInTranscript() {
        val transcript = json.decodeFromString<Transcript>(asset("sample_transcript.json"))
        val notes = json.decodeFromString<Notes>(asset("fake_notes.json"))
        val haystack = normalize(transcript.plainText)
        notes.facts.forEach { f ->
            assertTrue(
                "fake fact source_quote not found verbatim in transcript: \"${f.sourceQuote}\"",
                haystack.contains(normalize(f.sourceQuote)),
            )
        }
    }

    /** "missing" items must be real checklist labels for the hero matter (true set-difference). */
    @Test fun fakeMissingItemsAreRealChecklistLabels() {
        val lists = json.decodeFromString<List<Checklist>>(asset("checklists.json"))
        val notes = json.decodeFromString<Notes>(asset("fake_notes.json"))
        val contractLabels = lists.first { it.matterType == MatterType.CONTRACT_DISPUTE }
            .items.map { it.label }.toSet()
        notes.missing.forEach { m ->
            assertTrue("missing item is not a contract-dispute checklist label: \"$m\"", m in contractLabels)
        }
    }
}
