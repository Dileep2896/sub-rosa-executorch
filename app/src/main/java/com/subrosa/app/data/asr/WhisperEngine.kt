package com.subrosa.app.data.asr

import android.os.SystemClock
import android.util.Log
import com.subrosa.app.metrics.MetricsCollector
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The boundary over whisper.cpp (via [WhisperContext]/libwhisper.so). Loads a ggml model and
 * transcribes 16 kHz mono float audio entirely on-device. Mirrors LlmEngine: load once, reuse.
 */
class WhisperEngine(
    private val modelPath: String,
    private val metrics: MetricsCollector,
) {
    @Volatile
    private var ctx: WhisperContext? = null

    val isLoaded: Boolean get() = ctx != null

    suspend fun load() {
        if (ctx != null) return
        val t0 = SystemClock.elapsedRealtime()
        ctx = WhisperContext.createContextFromFile(modelPath)
        Log.i(TAG, "model loaded in ${SystemClock.elapsedRealtime() - t0} ms: ${File(modelPath).name}")
    }

    /** Transcribe normalized 16 kHz mono float samples → text. Reports ASR latency. Off the main thread. */
    suspend fun transcribe(samples: FloatArray): String {
        val active = ctx ?: error("WhisperEngine not loaded")
        val t0 = SystemClock.elapsedRealtime()
        val text = active.transcribeData(samples, printTimestamp = false).trim()
        metrics.reportAsrLatency(SystemClock.elapsedRealtime() - t0)
        return text
    }

    /**
     * Self-test: load (if needed) and transcribe a known WAV. Proves libwhisper.so + the ggml model +
     * the JNI bridge all work on-device — the ASR analog of the LLM warmup. Returns the transcript.
     */
    suspend fun selfTest(wavPath: String): String = withContext(Dispatchers.Default) {
        if (!isLoaded) load()
        val text = transcribe(decodeWavToMonoFloats(File(wavPath)))
        Log.i(TAG, "selfTest [${File(wavPath).name}] -> \"$text\"")
        text
    }

    suspend fun release() {
        ctx?.release()
        ctx = null
    }

    companion object {
        private const val TAG = "WhisperEngine"

        /** Decode a 16-bit PCM WAV (mono/stereo) into mono float32 in [-1,1]. */
        fun decodeWavToMonoFloats(file: File): FloatArray {
            val bytes = file.readBytes()
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val channels = bb.getShort(22).toInt().coerceAtLeast(1)
            val shorts = ShortArray(((bytes.size - WAV_HEADER_BYTES) / 2).coerceAtLeast(0))
            bb.position(WAV_HEADER_BYTES)
            bb.asShortBuffer().get(shorts)
            return if (channels <= 1) {
                FloatArray(shorts.size) { (shorts[it] / 32768f).coerceIn(-1f, 1f) }
            } else {
                FloatArray(shorts.size / channels) { i ->
                    ((shorts[i * channels] + shorts[i * channels + 1]) / 2f / 32768f).coerceIn(-1f, 1f)
                }
            }
        }

        /** Convert raw PCM16 (from AudioRecord) to normalized floats whisper expects. */
        fun pcm16ToFloats(pcm: ShortArray, length: Int = pcm.size): FloatArray =
            FloatArray(length) { (pcm[it] / 32768f).coerceIn(-1f, 1f) }

        private const val WAV_HEADER_BYTES = 44
    }
}
