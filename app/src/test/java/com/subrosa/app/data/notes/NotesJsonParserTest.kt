package com.subrosa.app.data.notes

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.ChecklistItem
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.NotesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesJsonParserTest {

    private val parser = NotesJsonParser()

    private val clean = """
        {"facts":[{"statement":"Client signed a lease","source_quote":"I signed the lease in March"}],
         "missing":["Witnesses"],
         "prompts":["Ask about the security deposit"]}
    """.trimIndent()

    @Test fun parsesCleanJson() {
        val r = parser.parse(clean)
        assertTrue("expected Parsed but was $r", r is NotesResult.Parsed)
        r as NotesResult.Parsed
        assertEquals(1, r.notes.facts.size)
        assertEquals("Client signed a lease", r.notes.facts[0].statement)
        assertEquals(listOf("Witnesses"), r.notes.missing)
        assertEquals(listOf("Ask about the security deposit"), r.notes.prompts)
    }

    @Test fun parsesFencedJson() {
        val fenced = "```json\n$clean\n```"
        assertTrue(parser.parse(fenced) is NotesResult.Parsed)
    }

    @Test fun parsesJsonWithTrailingProse() {
        val withProse = "$clean\n\nI hope this helps! Let me know if you need anything else."
        assertTrue(parser.parse(withProse) is NotesResult.Parsed)
    }

    @Test fun parsesJsonWithLeadingProse() {
        val withPreamble = "Sure, here is the JSON you asked for:\n$clean"
        assertTrue(parser.parse(withPreamble) is NotesResult.Parsed)
    }

    @Test fun garbageReturnsFailed() {
        assertTrue(parser.parse("I'm sorry, I cannot help with that.") is NotesResult.Failed)
    }

    @Test fun emptyReturnsFailed() {
        assertTrue(parser.parse("") is NotesResult.Failed)
    }

    @Test fun wrongTypeFieldSalvagesToPartial() {
        // 'missing' is a string, not an array -> strict decode fails -> field salvage -> Partial.
        val malformed = """{"facts":[],"missing":"none","prompts":[]}"""
        val r = parser.parse(malformed)
        assertTrue("expected Partial but was $r", r is NotesResult.Partial)
        r as NotesResult.Partial
        assertTrue(r.errors.any { it.contains("missing") })
    }

    @Test fun factMissingQuoteIsSalvaged() {
        val noQuote = """{"facts":[{"statement":"A fact with no quote"}],"missing":[],"prompts":[]}"""
        val r = parser.parse(noQuote)
        // source_quote is required, so strict fails; salvage keeps the fact with an empty quote.
        assertEquals("A fact with no quote", r.notesOrNull!!.facts.single().statement)
    }

    @Test fun quoteGuardKeepsVerifiedFactParsed() {
        val transcript = "CLIENT: I signed the lease in March. LAWYER: Okay."
        val verified = parser.verifyQuotes(parser.parse(clean), transcript)
        assertTrue(verified is NotesResult.Parsed)
        assertTrue(verified.notesOrNull!!.facts.all { it.verified })
    }

    @Test fun quoteGuardDemotesWhenQuoteAbsent() {
        val transcript = "CLIENT: We never discussed any lease."
        val verified = parser.verifyQuotes(parser.parse(clean), transcript)
        assertTrue("expected Partial but was $verified", verified is NotesResult.Partial)
        assertFalse(verified.notesOrNull!!.facts.first().verified)
    }

    @Test fun computesMissingFromCoverageWhenChecklistGiven() {
        val checklist = Checklist(
            MatterType.CONTRACT_DISPUTE, "Contract dispute",
            listOf(
                ChecklistItem("parties", "The parties to the agreement"),
                ChecklistItem("deadline", "The agreed deadline"),
                ChecklistItem("witnesses", "Any witnesses"),
            ),
        )
        // model reports the ids it covered; code computes missing = checklist − covered (as labels)
        val raw = """{"facts":[{"statement":"x","source_quote":"y"}],"covered":["parties","deadline"],"prompts":["ask"]}"""
        val r = parser.parse(raw, checklist)
        assertEquals(listOf("Any witnesses"), r.notesOrNull!!.missing)
        assertEquals(1, r.notesOrNull!!.facts.size)
        assertEquals(listOf("ask"), r.notesOrNull!!.prompts)
    }
}
