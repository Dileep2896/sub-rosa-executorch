package com.subrosa.app.llm

import android.os.SystemClock
import android.util.Log
import com.subrosa.app.metrics.MetricsCollector
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

/**
 * CPU/XNNPACK text engine — the always-available path (and the [NpuRunnerEngine] fallback). The ONLY
 * file that touches ExecuTorch (`org.pytorch.executorch.*`). Wraps [LlmModule] to load a local .pte
 * text model + tokenizer and run streaming generation off the main thread.
 *
 * API per ExecuTorch 1.3.1: `LlmModule(modelPath, tokenizerPath, temperature)` (text model),
 * `load()` (throws on failure), `generate(prompt, seqLen, callback, echo)`, `LlmCallback.onResult`.
 */
class LlmEngine(
    private val modelPath: String,
    private val tokenizerPath: String,
    private val metrics: MetricsCollector,
    private val backendLabel: String = "ExecuTorch · CPU (XNNPACK)",
    private val temperature: Float = 0f, // greedy → deterministic, reproducible reports (esp. for the demo)
) : TextEngine {
    @Volatile
    private var module: LlmModule? = null

    override val isLoaded: Boolean get() = module != null

    /** The CPU prompt seeds the assistant turn (PromptAssembler's JSON_PREFILL / "["), dropped by echo=false. */
    override val seedsAssistantTurn: Boolean get() = true

    override suspend fun load() = withContext(Dispatchers.Default) {
        if (module != null) return@withContext
        val t0 = SystemClock.elapsedRealtime()
        val loaded = LlmModule(modelPath, tokenizerPath, temperature)
        loaded.load() // throws if the .pte / tokenizer can't be loaded
        module = loaded
        metrics.setBackend(backendLabel)
        metrics.reportModelLoad(SystemClock.elapsedRealtime() - t0)
    }

    /** Runs generation, accumulating streamed tokens. Returns the full text. Blocking → off-main. */
    override suspend fun generate(prompt: String, seqLen: Int, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            if (module == null) load() // auto-load (matters when used as the NPU fallback)
            val active = module ?: error("LlmEngine not loaded")
            val sb = StringBuilder()
            val start = SystemClock.elapsedRealtime()
            var statsReported = false
            var liveTokens = 0
            // NOTE: per ExecuTorch, you must NOT call LlmModule methods from inside the callback
            // (other than stop()); we only accumulate text + report metrics here.
            val callback = object : LlmCallback {
                override fun onResult(result: String) {
                    sb.append(result)
                    // Drive a live tokens/sec ticker during generation (onStats only fires at the end).
                    liveTokens++
                    val elapsed = SystemClock.elapsedRealtime() - start
                    if (elapsed > 0) metrics.reportGeneration(tokenCount = liveTokens, elapsedMs = elapsed)
                    onToken(result)
                }
                override fun onStats(stats: String) {
                    // The runner reports a JSON of token counts + ms timestamps. Use it for an exact
                    // generation rate (the demo's headline tokens/sec) instead of a wall-clock estimate.
                    runCatching {
                        val o = JSONObject(stats)
                        val generated = o.optLong("generated_tokens", 0L)
                        val genMs = o.optLong("inference_end_ms", 0L) - o.optLong("prompt_eval_end_ms", 0L)
                        if (generated > 0L && genMs > 0L) {
                            metrics.reportGeneration(tokenCount = generated.toInt(), elapsedMs = genMs)
                            statsReported = true
                        }
                    }
                }
                override fun onError(errorCode: Int, message: String) { /* surfaced as empty output */ }
            }
            active.generate(prompt, seqLen, callback, false) // echo=false: only generated tokens
            if (!statsReported) {
                // Fallback when no stats arrived (e.g. an early error): rough wall-clock estimate.
                metrics.reportGeneration(
                    tokenCount = (sb.length / 4).coerceAtLeast(1),
                    elapsedMs = SystemClock.elapsedRealtime() - start,
                )
            }
            sb.toString()
        }

    /**
     * Loads the model if needed and runs a tiny generation from a fixed short prompt. Two purposes:
     * (1) warm the runtime so the first real consultation has no cold-load latency on the demo floor;
     * (2) a self-test that proves token generation works end-to-end. Returns the sample text.
     */
    override suspend fun warmup(): String {
        if (!isLoaded) load()
        val sample = generate("Once upon a time", seqLen = 64) {}
        Log.i(TAG, "warmup sample: ${sample.take(240)}")
        return sample
    }

    override fun close() {
        runCatching { module?.close() }
        module = null
    }

    private companion object {
        const val TAG = "LlmEngine"
    }
}
