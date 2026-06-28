package com.subrosa.app.data.asr

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/** A diarized region: who spoke (a stable cluster index) from [startSec] to [endSec]. */
data class DiarSegment(val startSec: Float, val endSec: Float, val speaker: Int)

/**
 * On-device speaker diarization over sherpa-onnx (ONNX Runtime, CPU) — pyannote segmentation-3.0 plus a
 * speaker-embedding model and fast clustering, fully offline. Given a 16 kHz mono float clip it returns
 * who-spoke-when. [numSpeakers] (the "people in the room" hint) is passed as the clustering target;
 * pass -1 when unknown to let [threshold] decide the count. Never throws — returns an empty list on any
 * failure so the caller can fall back to the manual speaker tap.
 */
class OnnxDiarizer(
    private val segmentationModelPath: String,
    private val embeddingModelPath: String,
    private val threshold: Float = 0.5f,
) {
    @Volatile private var engine: OfflineSpeakerDiarization? = null
    private var loadedFor: Int = UNSET

    /** True only when both model files are actually present (pushed alongside the LLM/whisper models). */
    val available: Boolean
        get() = File(segmentationModelPath).exists() && File(embeddingModelPath).exists()

    /** Build (or rebuild, if the speaker count changed) the diarizer. Safe to call repeatedly. */
    @Synchronized
    fun ensureLoaded(numSpeakers: Int) {
        if (engine != null && loadedFor == numSpeakers) return
        engine?.release(); engine = null
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segmentationModelPath),
                numThreads = 4, debug = false, provider = "cpu",
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = embeddingModelPath, numThreads = 2, debug = false, provider = "cpu",
            ),
            clustering = FastClusteringConfig(numClusters = numSpeakers, threshold = threshold),
            minDurationOn = 0.3f,
            minDurationOff = 0.5f,
        )
        engine = OfflineSpeakerDiarization(assetManager = null, config = config)
        loadedFor = numSpeakers
        Log.i(TAG, "loaded (numSpeakers=$numSpeakers, sampleRate=${engine?.sampleRate()})")
    }

    /** Diarize a whole 16 kHz mono float clip. Returns [] on any error (caller falls back to manual tap). */
    @Synchronized
    fun diarize(samples: FloatArray, numSpeakers: Int): List<DiarSegment> {
        return try {
            ensureLoaded(numSpeakers)
            val active = engine ?: return emptyList()
            val t0 = SystemClock.elapsedRealtime()
            val out = active.process(samples).map { DiarSegment(it.start, it.end, it.speaker) }
            Log.i(
                TAG,
                "diarized ${samples.size} samples in ${SystemClock.elapsedRealtime() - t0}ms → " +
                    "${out.size} segments, speakers=${out.map { it.speaker }.distinct().sorted()}",
            )
            out
        } catch (t: Throwable) {
            Log.e(TAG, "diarize failed", t)
            emptyList()
        }
    }

    @Synchronized
    fun release() {
        engine?.release(); engine = null; loadedFor = UNSET
    }

    companion object {
        private const val TAG = "OnnxDiarizer"
        private const val UNSET = -2
    }
}
