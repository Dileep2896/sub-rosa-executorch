package com.subrosa.app.llm

import android.util.Log
import com.subrosa.app.metrics.MetricsCollector

/**
 * Runs [primary] (the Hexagon NPU) and, the first time it throws, transparently and permanently
 * switches to [fallbackProvider] (CPU / XNNPACK) for the rest of the session — flipping the backend
 * chip to a red "CPU FALLBACK" so the demo stays honest and never dies on a DSP hiccup. This is the
 * plan's backend-assertion safety net made concrete: NPU when it works, CPU when it doesn't, never a lie.
 */
class FallbackTextEngine(
    private val primary: TextEngine,
    private val fallbackProvider: () -> TextEngine,
    private val metrics: MetricsCollector,
) : TextEngine {

    @Volatile private var degraded = false
    private val fallback: TextEngine by lazy { fallbackProvider() }
    private val active: TextEngine get() = if (degraded) fallback else primary

    override val isLoaded: Boolean get() = active.isLoaded
    override val seedsAssistantTurn: Boolean get() = active.seedsAssistantTurn

    override suspend fun load() { guard { active.load() } }

    override suspend fun generate(prompt: String, seqLen: Int, onToken: (String) -> Unit): String =
        guard { active.generate(prompt, seqLen, onToken) }

    override suspend fun warmup(): String = guard { active.warmup() }

    override fun close() {
        runCatching { primary.close() }
        if (degraded) runCatching { fallback.close() }
    }

    /** Run [block]; on the first failure switch to CPU (red chip) and retry once on the fallback. */
    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (t: Throwable) {
        if (degraded) throw t
        Log.w(TAG, "Hexagon NPU engine failed (${t.message}) — switching to CPU for the session", t)
        degraded = true
        metrics.setBackend(CPU_FALLBACK_LABEL)
        block() // `active` now resolves to the CPU fallback (which loads itself on demand)
    }

    private companion object {
        const val TAG = "FallbackTextEngine"
        const val CPU_FALLBACK_LABEL = "CPU FALLBACK"
    }
}
