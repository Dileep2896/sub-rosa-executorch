package com.subrosa.app.data.demo

import com.subrosa.app.data.cases.CaseStore
import com.subrosa.app.data.client.ClientStore
import com.subrosa.app.domain.model.Case
import com.subrosa.app.domain.model.Client
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.domain.model.TranscriptSegment

/**
 * Seeds the demo client + case (Maya Okonkwo / Trellis website contract) so it's always selectable from
 * the client list. Idempotent — fixed ids, so re-running overwrites, never duplicates. The demo runs the
 * REAL on-device LLM over these scripts: [openingScript] deliberately leaves the *witnesses* and
 * *mitigation* checklist items uncovered, and [followUpFor] streams the Q&A that fills whatever the
 * report actually flagged as missing — so the follow-up stays in sync with the live model every run.
 */
class DemoSeeder(
    private val clientStore: ClientStore,
    private val caseStore: CaseStore,
) {
    fun seed(nowMs: Long): Case {
        clientStore.save(
            Client(
                id = CLIENT_ID,
                name = "Maya Okonkwo",
                phone = "(415) 555-0182",
                email = "maya@okonkwobakery.com",
                address = "1190 Valencia St, San Francisco, CA",
                note = "Bakery website dispute · demo",
                createdAtMs = nowMs,
            ),
        )
        val case = Case(
            id = CASE_ID,
            clientId = CLIENT_ID,
            matterType = MatterType.CONTRACT_DISPUTE,
            title = "Trellis Studio website contract",
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        caseStore.save(case)
        return case
    }

    companion object {
        const val CLIENT_ID = "demo-maya"
        const val CASE_ID = "demo-trellis"

        private fun seg(s: Speaker, t: String, conf: Float = 1f) =
            TranscriptSegment(speaker = s, text = t, confidence = conf)

        /**
         * Script 1 — the opening consultation. Covers 8 of the 10 contract-dispute checklist items but
         * deliberately leaves WITNESSES and MITIGATION uncovered, so the real model flags them as the two
         * gaps. (It also slips in a conflicting deadline — "last Friday" vs "the eighth" — for the model
         * to catch.)
         */
        fun openingScript() = Transcript(
            listOf(
                seg(Speaker.LAWYER, "Thanks for coming in, Maya. Tell me what's been going on."),
                seg(Speaker.CLIENT, "So I hired a company called Trellis Studio to build a website for my bakery, and they never finished it."),
                seg(Speaker.LAWYER, "Okay. What did the two of you agree to?"),
                seg(Speaker.CLIENT, "We agreed they'd deliver the finished website for four thousand dollars, and I paid two thousand up front as a deposit."),
                seg(Speaker.LAWYER, "When did you first agree to the work?"),
                seg(Speaker.CLIENT, "It was back in March, March 8th I think. They were supposed to have it done by last Friday, but they just stopped responding to me."),
                seg(Speaker.LAWYER, "Did you sign a contract?"),
                seg(Speaker.CLIENT, "Honestly, I'm not sure if we ever signed an actual contract, or if it was all just over email."),
                seg(Speaker.LAWYER, "And you said the deadline was last Friday?"),
                seg(Speaker.CLIENT, "Well, the deadline, I keep second-guessing myself, but I think they actually promised it by the eighth.", 0.55f),
                seg(Speaker.LAWYER, "Do you have the paperwork, the agreement, the invoice for your deposit?"),
                seg(Speaker.CLIENT, "I've got all our emails saved, but I cannot find the invoice for my deposit anywhere. That two thousand dollars is just gone, and now I'll have to pay someone else to finish the site."),
                seg(Speaker.LAWYER, "Okay. Let me make sure I understand the timeline and what you have in writing."),
            ),
        )

        /** Back-compat alias (DemoTranscriber's default transcript). */
        fun transcript() = openingScript()

        /**
         * Script 2 fragments — keyed by the EXACT checklist label. SessionViewModel.loadScript reads the
         * report's actual `missing` list and streams the matching Q&A, so the continuation always fills
         * precisely what the live model flagged — never a guess that can drift out of sync.
         */
        private val FOLLOW_UP: Map<String, List<TranscriptSegment>> = mapOf(
            "Any witnesses" to listOf(
                seg(Speaker.LAWYER, "Picking up from last time, Maya — was anyone else there when you and Trellis agreed on the work?"),
                seg(Speaker.CLIENT, "Yes — my business partner, Devin Cole, was on the video call when we agreed on the four thousand dollars. And my sister saw the half-finished site when she stopped by the shop."),
            ),
            "Whether the client tried to resolve it / mitigate" to listOf(
                seg(Speaker.LAWYER, "And since they went quiet, what have you done to try to sort it out or limit the damage?"),
                seg(Speaker.CLIENT, "I emailed them five times and called twice — nothing back. So I've started getting quotes from another developer, about fifteen hundred dollars to finish it."),
            ),
            "Financial loss or damages claimed" to listOf(
                seg(Speaker.LAWYER, "Can you put a number on what this has cost you so far?"),
                seg(Speaker.CLIENT, "The two thousand deposit is gone, plus about fifteen hundred to have someone else finish it — so roughly three and a half thousand."),
            ),
            "Any documents or evidence (invoices, emails, signed copy)" to listOf(
                seg(Speaker.LAWYER, "What do you actually have in writing from all of this?"),
                seg(Speaker.CLIENT, "Every email is saved with dates, and I have screenshots of the deposit payment from my bank — just not a formal invoice."),
            ),
            "Date the agreement was made" to listOf(
                seg(Speaker.LAWYER, "Let's pin down the date — when exactly did you agree to the work?"),
                seg(Speaker.CLIENT, "March 8th. I remember because it was right after my supplier meeting that morning."),
            ),
        )

        /** The follow-up Q&A for one flagged checklist label, or empty if we have no scripted answer for it. */
        fun followUpFor(missingLabel: String): List<TranscriptSegment> = FOLLOW_UP[missingLabel].orEmpty()

        /** Fallback follow-up (the two designed gaps) if the report flagged nothing we have a fragment for. */
        fun defaultFollowUp(): List<TranscriptSegment> =
            followUpFor("Any witnesses") + followUpFor("Whether the client tried to resolve it / mitigate")
    }
}
