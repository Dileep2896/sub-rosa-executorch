package com.subrosa.app.data.asr

import android.os.SystemClock
import android.util.Log
import com.subrosa.app.domain.Transcriber
import com.subrosa.app.domain.TranscriptionEvent
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * On-device transcriber over whisper.cpp. Whisper is a *batch* model, so we feed it audio in fixed
 * ~[windowSeconds] blocks and transcribe each block **exactly once**, emitting it as a finalized line.
 * Transcribing once (rather than re-reading an ever-growing buffer) bounds the work per block, so
 * transcription stays ahead of real time instead of accumulating lag — a few seconds of tiny.en
 * transcribe in well under a second on the Snapdragon, so a fresh line lands roughly every
 * [windowSeconds]. For a live "types out" feel, the growing block is re-transcribed every
 * ~[partialSeconds] as an updating [TranscriptionEvent.Partial]; the full block then replaces it as a
 * [TranscriptionEvent.SegmentFinal], attributed to the lawyer's current tap speaker. On stop, the
 * trailing block is flushed.
 */
class WhisperTranscriber(
    private val engine: WhisperEngine,
    private val windowSeconds: Float = 2.8f,
) : Transcriber {

    override fun transcribe(
        audio: Flow<ShortArray>,
        speakerProvider: () -> Speaker,
    ): Flow<TranscriptionEvent> = flow {
        Log.i(TAG, "transcribe() started — ensuring whisper is loaded")
        val loaded = runCatching { engine.load() }
        if (loaded.isFailure) {
            Log.e(TAG, "whisper load failed", loaded.exceptionOrNull())
            emit(TranscriptionEvent.Error("ASR model failed to load: ${loaded.exceptionOrNull()?.message}"))
            return@flow
        }
        Log.i(TAG, "whisper ready — listening in ${windowSeconds}s windows")

        val windowSamples = (SAMPLE_RATE * windowSeconds).toInt()
        val chunks = ArrayList<ShortArray>()
        var samples = 0
        var chunkCount = 0
        var baseSamples = 0L // absolute samples committed in prior windows — gives each segment its timestamp

        // Transcribe the currently-buffered audio once; logs size, latency, and the real-time factor
        // (rtf>1 means faster than real time — i.e. we are keeping up).
        suspend fun run(label: String, floats: FloatArray): String {
            val n = floats.size
            val t0 = SystemClock.elapsedRealtime()
            val raw = runCatching { engine.transcribe(floats) }
            val dt = SystemClock.elapsedRealtime() - t0
            raw.exceptionOrNull()?.let { Log.e(TAG, "transcribe threw on ${n}smp (${dt}ms)", it) }
            val text = clean(raw.getOrDefault(""))
            val rtf = if (dt > 0) n / (16f * dt) else 999f
            Log.i(TAG, "$label ${n}smp ${dt}ms rtf=${"%.1f".format(rtf)}x -> \"$text\"")
            return text
        }

        audio.collect { chunk ->
            chunkCount++
            if (chunkCount == 1) Log.i(TAG, "first audio chunk: ${chunk.size} samples")
            chunks.add(chunk)
            samples += chunk.size
            if (samples >= windowSamples) {
                val floats = WhisperEngine.pcm16ToFloats(merge(chunks, samples))
                val text = run("FINAL", floats)
                val startMs = baseSamples / 16
                val endMs = (baseSamples + samples) / 16
                baseSamples += samples
                chunks.clear(); samples = 0
                if (text.isNotEmpty()) {
                    emit(TranscriptionEvent.SegmentFinal(TranscriptSegment(speaker = speakerProvider(), text = text, startMs = startMs, endMs = endMs), audio = floats))
                }
            }
        }
        // finalize whatever is buffered when recording stops
        Log.i(TAG, "audio ended after $chunkCount chunks; flushing $samples samples")
        if (samples > 0) {
            val floats = WhisperEngine.pcm16ToFloats(merge(chunks, samples))
            val text = run("TAIL", floats)
            val startMs = baseSamples / 16
            val endMs = (baseSamples + samples) / 16
            if (text.isNotEmpty()) {
                emit(TranscriptionEvent.SegmentFinal(TranscriptSegment(speaker = speakerProvider(), text = text, startMs = startMs, endMs = endMs), audio = floats))
            }
        }
    }

    private fun clean(s: String): String {
        // Whisper emits non-speech annotations like [BLANK_AUDIO], [inaudible], (audience chattering),
        // (speaking in foreign language) — strip any bracketed/parenthesized group; if nothing real is
        // left, the segment was just silence or noise.
        return s.replace(NON_SPEECH, " ").replace(WHITESPACE, " ").trim()
    }

    private fun merge(chunks: List<ShortArray>, total: Int): ShortArray {
        val out = ShortArray(total)
        var offset = 0
        for (c in chunks) {
            c.copyInto(out, offset)
            offset += c.size
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "WhisperTx"
        /** Matches a whole `[...]` or `(...)` non-speech annotation. */
        private val NON_SPEECH = Regex("[\\[(][^\\])]*[\\])]")
        private val WHITESPACE = Regex("\\s+")
    }
}
