package com.subrosa.app.data.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Real microphone capture: AudioRecord → Flow<ShortArray> of 16 kHz mono PCM16 — exactly the format
 * whisper wants. The caller must hold RECORD_AUDIO (granted on the consent screen). Reads on a
 * dedicated thread; an unbounded buffer absorbs ASR that runs slower than real time, and cancelling
 * the collector stops + releases the recorder via awaitClose.
 */
class AudioRecorder(private val sampleRate: Int = 16000) {

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested/enforced on the consent screen
    fun stream(): Flow<ShortArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        val running = AtomicBoolean(true)
        val readSize = sampleRate / 2 // ~0.5s per read
        recorder.startRecording()
        val worker = thread(name = "subrosa-mic", start = true) {
            val buf = ShortArray(readSize)
            while (running.get()) {
                val n = recorder.read(buf, 0, buf.size)
                if (n > 0) trySend(buf.copyOf(n))
            }
        }

        awaitClose {
            running.set(false)
            runCatching { worker.join(500) }
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)
}
