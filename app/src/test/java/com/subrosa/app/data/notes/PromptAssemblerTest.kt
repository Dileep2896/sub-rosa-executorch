package com.subrosa.app.data.notes

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.ChecklistItem
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.domain.model.TranscriptSegment
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    private val checklist = Checklist(
        matterType = MatterType.CONTRACT_DISPUTE,
        displayName = "Contract dispute",
        items = listOf(
            ChecklistItem("witnesses", "Any witnesses to the agreement"),
            ChecklistItem("mitigation", "Steps taken to mitigate losses"),
        ),
    )
    private val transcript = Transcript(
        listOf(
            TranscriptSegment(Speaker.LAWYER, "What happened?"),
            TranscriptSegment(Speaker.CLIENT, "We signed the lease in March."),
        ),
    )

    @Test
    fun assembleIncludesChecklistTranscriptAndSchema() {
        val prompt = PromptAssembler().assemble(transcript, checklist)
        // injected checklist (the legal competence) is present
        assertTrue(prompt.contains("Contract dispute"))
        assertTrue(prompt.contains("Any witnesses to the agreement"))
        assertTrue(prompt.contains("Steps taken to mitigate losses"))
        // speaker-labeled transcript is present
        assertTrue(prompt.contains("CLIENT: We signed the lease in March."))
        assertTrue(prompt.contains("LAWYER: What happened?"))
        // strict-JSON schema is instructed
        assertTrue(prompt.contains("\"facts\""))
        assertTrue(prompt.contains("\"covered\""))
        assertTrue(prompt.contains("\"prompts\""))
        assertTrue(prompt.contains("source_quote"))
        assertTrue(prompt.contains("[witnesses]")) // checklist items are listed with their ids
        // the "reason only over provided text" guardrail must survive
        assertTrue(prompt.contains("Use ONLY facts stated in"))
    }

    @Test
    fun llama3WrapsWithChatTemplate() {
        val prompt = PromptAssembler().assembleLlama3(transcript, checklist)
        assertTrue(prompt.startsWith("<|begin_of_text|>"))
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>assistant<|end_header_id|>"))
        assertTrue(prompt.trimEnd().endsWith("[")) // assistant turn seeded with the JSON prefill
        // the user content still lives inside the template
        assertTrue(prompt.contains("Any witnesses to the agreement"))
    }
}
