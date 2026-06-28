package com.subrosa.app.llm

/**
 * The narrow contract the app's LLM call sites (notes generation, speaker attribution) depend on, so
 * the CPU path ([LlmEngine] — ExecuTorch XNNPACK) and the Hexagon-NPU path ([NpuRunnerEngine] — the
 * qnn_llama_runner subprocess) are interchangeable. AppContainer picks the implementation by which
 * .pte is present; [FallbackTextEngine] composes the two so the demo survives a DSP failure.
 */
interface TextEngine {

    val isLoaded: Boolean

    /**
     * True when the engine consumes a fully chat-templated prompt whose assistant turn is *seeded*
     * (PromptAssembler's `JSON_PREFILL` / `"["`) and the seed is NOT echoed back — so callers must
     * prepend it to the returned text. False when the engine re-templates a raw prompt itself (the
     * runner does), so the model emits the whole answer and callers prepend nothing.
     */
    val seedsAssistantTurn: Boolean

    suspend fun load()

    /** Run generation and return the full generated text. [onToken] streams tokens when supported. */
    suspend fun generate(prompt: String, seqLen: Int, onToken: (String) -> Unit = {}): String

    /** Load (if needed) + a tiny generation to warm the runtime and prove generation works end-to-end. */
    suspend fun warmup(): String

    fun close()
}
