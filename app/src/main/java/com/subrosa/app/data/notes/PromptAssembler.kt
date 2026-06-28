package com.subrosa.app.data.notes

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.domain.model.TranscriptSegment

/** Chat-template family of the loaded model (set per exported model in AppContainer). */
enum class PromptStyle { PLAIN, LLAMA3, CHATML }

/**
 * Builds the LLM prompt from human-authored scaffolding + the transcript. The legal competence lives
 * here in text (the system prompt + the injected checklist), NOT in the weights — the model only
 * extracts, reformats, and computes set-difference over the provided transcript.
 */
class PromptAssembler {

    /** The checklist + transcript + output instruction (the "user" turn content). */
    fun userBlock(transcript: Transcript, checklist: Checklist): String = buildString {
        appendLine("INTAKE CHECKLIST (${checklist.displayName}) — each item is shown as [id] label:")
        appendLine(checklist.items.joinToString("\n") { "- [${it.id}] ${it.label}" })
        appendLine()
        appendLine("TRANSCRIPT:")
        appendLine(transcript.labeled)
        appendLine()
        append(OUTPUT_INSTRUCTION)
    }

    /** Plain system+user prompt (for base / non-chat models). */
    fun assemble(transcript: Transcript, checklist: Checklist): String =
        "$SYSTEM\n\n${userBlock(transcript, checklist)}"

    /** Llama-3 chat-formatted prompt — what an instruct model expects (generate() applies no template). */
    fun assembleLlama3(transcript: Transcript, checklist: Checklist): String {
        val user = userBlock(transcript, checklist)
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$SYSTEM<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n$JSON_PREFILL"
    }

    /**
     * ChatML prompt — for Qwen / SmolLM2 instruct models. The assistant turn is seeded with an empty
     * `<think></think>` block (Qwen3's "no-think" switch — otherwise it reasons in a first JSON, emits
     * `</think>`, then starts a second JSON that runs past the token cap) followed by the JSON opening.
     */
    fun assembleChatML(transcript: Transcript, checklist: Checklist): String {
        val user = userBlock(transcript, checklist)
        return "<|im_start|>system\n$SYSTEM<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n$JSON_PREFILL"
    }

    /** Dispatch to the template matching the loaded model. */
    fun forStyle(transcript: Transcript, checklist: Checklist, style: PromptStyle): String = when (style) {
        PromptStyle.PLAIN -> assemble(transcript, checklist)
        PromptStyle.LLAMA3 -> assembleLlama3(transcript, checklist)
        PromptStyle.CHATML -> assembleChatML(transcript, checklist)
    }

    /**
     * The speaker-attribution turn: number every transcript line and ask the model to label each
     * L (lawyer — guides/asks) or C (client — narrates/answers), returning only a JSON array. The
     * assistant turn is seeded with "[" so it emits the array straight away. Pairs with
     * [com.subrosa.app.data.notes.SpeakerAttributor].
     */
    fun assembleSpeakerAttribution(segments: List<TranscriptSegment>, style: PromptStyle): String {
        val user = buildString {
            appendLine("These are the numbered lines of a lawyer–client first-meeting transcript, in order:")
            segments.forEachIndexed { i, s -> appendLine("${i + 1}. ${s.text}") }
            appendLine()
            appendLine("Label EACH line by who is speaking: L = the LAWYER (guides the meeting, asks questions), C = the CLIENT (describes their situation, answers).")
            append("Output ONLY a JSON array of ${segments.size} items, each \"L\" or \"C\", in line order. Example: [\"L\",\"C\",\"C\"].")
        }
        return when (style) {
            PromptStyle.PLAIN -> "$ATTRIB_SYSTEM\n\n$user\n["
            PromptStyle.LLAMA3 ->
                "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$ATTRIB_SYSTEM<|eot_id|>" +
                    "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
                    "<|start_header_id|>assistant<|end_header_id|>\n\n["
            PromptStyle.CHATML ->
                "<|im_start|>system\n$ATTRIB_SYSTEM<|im_end|>\n" +
                    "<|im_start|>user\n$user<|im_end|>\n" +
                    "<|im_start|>assistant\n<think>\n\n</think>\n\n["
        }
    }

    companion object {
        val SYSTEM = """
            You are a legal intake assistant. You read the transcript of a lawyer's first meeting with a
            client and return structured intake notes as a single JSON object. Use ONLY facts stated in
            the transcript. Never state the law, give advice, or invent details. Fill every field with
            real content from the transcript — do not repeat these instructions or echo the placeholders.
        """.trimIndent()

        val OUTPUT_INSTRUCTION = """
            Produce ONE JSON object with exactly these keys, filled with REAL content from the transcript:
            "facts": a list of {"statement": "<a fact the client stated, plain language>", "source_quote": "<exact words from the transcript>"}
            "covered": a list of the checklist [id]s that the transcript actually addressed (copy the bracketed ids)
            "prompts": a list of strings — short neutral follow-up questions for the lawyer to ask
            Output ONLY the JSON object. Do not repeat these instructions.
        """.trimIndent()

        /** Seeds the assistant turn with the JSON opening so even small models emit valid array structure. */
        const val JSON_PREFILL = "{\"facts\": ["

        /** System line for the speaker-attribution pass (see [assembleSpeakerAttribution]). */
        const val ATTRIB_SYSTEM =
            "You identify who is speaking in a legal intake conversation. Reply with only the JSON array of L/C labels."
    }
}
