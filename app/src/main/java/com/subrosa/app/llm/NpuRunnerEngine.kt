package com.subrosa.app.llm

import android.content.Context
import android.util.Log
import com.subrosa.app.metrics.MetricsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Hexagon-NPU text engine. The QNN-delegated `model-qnn.pte` exposes a `kv_forward` + quantized-KV-cache
 * contract that the generic ExecuTorch `LlmModule` cannot drive — only Qualcomm's `qnn_llama_runner`
 * can. We ship that runner in `jniLibs` as `libqnn_llama_runner.so` (so Android extracts it to the
 * executable `nativeLibraryDir`) and exec it as a subprocess per generation, pointing LD_LIBRARY_PATH /
 * ADSP_LIBRARY_PATH at the QNN libs beside it. The app's ChatML prompt is split back into system/user
 * (the runner applies its own Qwen3 template), and tokens/sec + load time come from the runner's stats.
 *
 * Note: the runner returns its output in a file at the end (no token streaming) and the QNN model is
 * compiled for a 1024-token context, so [generate] caps `seq_len` accordingly.
 */
class NpuRunnerEngine(
    context: Context,
    private val modelPath: String,
    private val tokenizerPath: String,
    private val metrics: MetricsCollector,
    private val backendLabel: String = "Hexagon NPU · QNN",
    private val decoderModelVersion: String = "qwen3",
) : TextEngine {

    private val libDir = File(context.applicationInfo.nativeLibraryDir)
    private val runnerBin = File(libDir, "libqnn_llama_runner.so")
    private val workDir = File(context.cacheDir, "npu").apply { mkdirs() }

    @Volatile private var loaded = false
    override val isLoaded: Boolean get() = loaded

    /** The runner re-templates a raw prompt itself, so the model emits the whole answer — nothing to prepend. */
    override val seedsAssistantTurn: Boolean get() = false

    override suspend fun load(): Unit = withContext(Dispatchers.Default) {
        check(runnerBin.exists()) { "qnn_llama_runner missing at ${runnerBin.absolutePath}" }
        check(File(modelPath).exists()) { "NPU model missing: $modelPath" }
        metrics.setBackend(backendLabel)
        loaded = true
    }

    override suspend fun generate(prompt: String, seqLen: Int, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            if (!loaded) load()
            val (system, user) = splitChatML(prompt)
            val outFile = File(workDir, "out.txt").apply { delete() }
            val perfFile = File(workDir, "perf.txt").apply { delete() }

            val cmd = mutableListOf(
                runnerBin.absolutePath,
                "--model_path", modelPath,
                "--tokenizer_path", tokenizerPath,
                "--eval_mode", "0", // 0 = TokenGenerator (kv); our .pte only has kv_forward (no prefill method)
                "--decoder_model_version", decoderModelVersion,
                "--seq_len", seqLen.coerceIn(MIN_SEQ_LEN, MAX_CONTEXT).toString(),
                "--temperature", "0",
                "--output_path", outFile.absolutePath,
                "--performance_output_path", perfFile.absolutePath,
                // /no_think keeps Qwen3 from emitting a <think> block before the JSON.
                "--prompt", if (user.isBlank()) prompt.trim() else "$user /no_think",
            )
            if (system.isNotBlank()) { cmd += "--system_prompt"; cmd += system }

            val proc = ProcessBuilder(cmd)
                .directory(workDir)
                .redirectErrorStream(true)
                .also {
                    it.environment()["LD_LIBRARY_PATH"] = libDir.absolutePath
                    it.environment()["ADSP_LIBRARY_PATH"] = libDir.absolutePath
                }
                .start()
            val log = proc.inputStream.bufferedReader().use { r -> r.readText() }
            val exit = proc.waitFor()

            // Real load time + tokens/sec from the runner's PyTorchObserver stats line (the demo headline).
            parseStats(log)?.let { (genTokens, genMs, loadMs) ->
                if (loadMs > 0L) metrics.reportModelLoad(loadMs)
                if (genTokens > 0 && genMs > 0L) metrics.reportGeneration(genTokens, genMs)
            }

            val text = extractAssistant(outFile.takeIf { it.exists() }?.readText().orEmpty())
            Log.i(TAG, "npu run exit=$exit chars=${text.length} sample=${text.take(120).replace('\n', ' ')}")
            if (text.isBlank()) error("qnn_llama_runner produced no output (exit=$exit). tail: ${log.takeLast(500)}")
            text
        }

    override suspend fun warmup(): String {
        if (!loaded) load()
        val sample = generate(
            "<|im_start|>user\nSay hello in five words.<|im_end|>\n<|im_start|>assistant\n",
            seqLen = 48,
        )
        Log.i(TAG, "warmup sample: ${sample.take(160)}")
        return sample
    }

    override fun close() { loaded = false }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Recover SYSTEM + USER text from the app's ChatML prompt (the runner re-applies the Qwen3 template). */
    private fun splitChatML(prompt: String): Pair<String, String> {
        fun between(tag: String): String? {
            val open = "<|im_start|>$tag\n"
            val start = prompt.indexOf(open)
            if (start < 0) return null
            val from = start + open.length
            val end = prompt.indexOf("<|im_end|>", from)
            return if (end < 0) prompt.substring(from) else prompt.substring(from, end)
        }
        return (between("system")?.trim().orEmpty()) to (between("user")?.trim().orEmpty())
    }

    /** The runner's output file echoes the rendered template; keep only the assistant's generated text. */
    private fun extractAssistant(raw: String): String {
        if (raw.isBlank()) return ""
        val marker = "<|im_start|>assistant"
        val afterAssistant = raw.lastIndexOf(marker).let { if (it >= 0) raw.substring(it + marker.length) else raw }
        val afterThink = if ("</think>" in afterAssistant) afterAssistant.substringAfterLast("</think>") else afterAssistant
        return afterThink.replace("<|im_end|>", "").replace("<|endoftext|>", "").trim()
    }

    /** generated_tokens, generation ms, and model-load ms from the runner's PyTorchObserver JSON line. */
    private fun parseStats(log: String): Triple<Int, Long, Long>? {
        val line = log.lineSequence().firstOrNull { it.contains("PyTorchObserver") && it.contains('{') } ?: return null
        return runCatching {
            val o = JSONObject(line.substring(line.indexOf('{')))
            val gen = o.optInt("generated_tokens", 0)
            val genMs = o.optLong("inference_end_ms") - o.optLong("prompt_eval_end_ms")
            val loadMs = o.optLong("model_load_end_ms") - o.optLong("model_load_start_ms")
            Triple(gen, genMs, loadMs)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "NpuRunnerEngine"
        const val MAX_CONTEXT = 1024 // model-qnn.pte is compiled for a 1024-token context
        const val MIN_SEQ_LEN = 64
    }
}
