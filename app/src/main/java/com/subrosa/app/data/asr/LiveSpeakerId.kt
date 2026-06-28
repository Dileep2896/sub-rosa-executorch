package com.subrosa.app.data.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import kotlin.math.sqrt

/**
 * Live, lightweight speaker identification for the running transcript. For each finalized ~3 s segment
 * we compute a voice embedding (sherpa-onnx + the wespeaker model) and match it against the voices heard
 * so far by cosine against a running-average voiceprint per speaker; a sufficiently different voice
 * becomes a new speaker, up to the room's speaker count. Cheap enough to keep up live (tens of ms per
 * segment), unlike the full pyannote pass we still run accurately at Stop. Returns a stable cluster
 * index per voice, or -1 when it can't tell. Never throws. Logs the similarities so [threshold] can be
 * tuned to real voices.
 */
class LiveSpeakerId(
    private val embeddingModelPath: String,
    private val threshold: Float = 0.78f, // cosine; below this a turn is treated as a new voice
) {
    val available: Boolean get() = File(embeddingModelPath).exists()

    private var extractor: SpeakerEmbeddingExtractor? = null
    private val centroids = ArrayList<FloatArray>() // running-average voiceprint per speaker
    private val counts = ArrayList<Int>()

    @Synchronized
    private fun ensure() {
        if (extractor != null) return
        extractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = embeddingModelPath, numThreads = 2, debug = false, provider = "cpu",
            ),
        )
    }

    /** Clear the voices heard so far — call when a new recording starts. Keeps the model loaded. */
    @Synchronized
    fun reset() {
        ensure(); centroids.clear(); counts.clear()
    }

    /** Embed [samples], match against known voices, and return a stable cluster index (or -1). */
    @Synchronized
    fun assign(samples: FloatArray, maxSpeakers: Int): Int {
        val emb = embed(samples) ?: return -1
        val sims = centroids.map { cosine(emb, it) }
        val bestIdx = sims.indices.maxByOrNull { sims[it] } ?: -1
        val bestSim = if (bestIdx >= 0) sims[bestIdx] else -1f
        Log.i(
            TAG,
            "assign: ${centroids.size} known, sims=[${sims.joinToString { "%.2f".format(it) }}] " +
                "best=$bestIdx@${"%.2f".format(bestSim)} thr=$threshold maxSpk=$maxSpeakers",
        )
        if (bestIdx >= 0 && bestSim >= threshold) { merge(bestIdx, emb); return bestIdx }
        if (centroids.size < maxSpeakers.coerceAtLeast(1)) {
            centroids.add(emb.copyOf()); counts.add(1)
            return centroids.size - 1
        }
        return bestIdx.coerceAtLeast(0) // at capacity → nearest known voice
    }

    /** Raw voice embedding for [samples] (16 kHz mono float), or null if undecidable. */
    @Synchronized
    fun embed(samples: FloatArray): FloatArray? = try {
        ensure()
        val ex = extractor
        if (ex == null) null else {
            val stream = ex.createStream()
            stream.acceptWaveform(samples, 16000)
            stream.inputFinished()
            if (!ex.isReady(stream)) { Log.i(TAG, "embed: not enough audio (${samples.size} samples)"); stream.release(); null }
            else { val e = ex.compute(stream); stream.release(); e }
        }
    } catch (t: Throwable) { Log.e(TAG, "embed failed", t); null }

    private fun merge(i: Int, emb: FloatArray) {
        val c = centroids[i]; val n = counts[i]
        for (k in c.indices) c[k] = (c[k] * n + emb[k]) / (n + 1)
        counts[i] = n + 1
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / (sqrt(na) * sqrt(nb) + 1e-9f)
    }

    @Synchronized
    fun release() {
        extractor?.release(); extractor = null; centroids.clear(); counts.clear()
    }

    companion object { private const val TAG = "LiveSpeakerId" }
}
