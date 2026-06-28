package com.subrosa.app.data.demo

import com.subrosa.app.domain.Transcriber
import com.subrosa.app.domain.TranscriptionEvent
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.Transcript
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Replays the demo consultation transcript paced to roughly track `demo-consultation.wav`, so in demo
 * mode the live transcript appears in step with the audio. Each segment carries its own speaker.
 */
class DemoTranscriber(
    private val transcript: Transcript = DemoSeeder.transcript(),
    private val spanMs: Long = 72_000L,
    private val minMs: Long = 1300L,
    private val maxMs: Long = 11_000L,
) : Transcriber {

    override fun transcribe(
        audio: Flow<ShortArray>,
        speakerProvider: () -> Speaker,
    ): Flow<TranscriptionEvent> = flow {
        val totalChars = transcript.segments.sumOf { it.text.length }.coerceAtLeast(1)
        for (seg in transcript.segments) {
            val d = (seg.text.length.toLong() * spanMs / totalChars).coerceIn(minMs, maxMs)
            delay(d)
            emit(TranscriptionEvent.SegmentFinal(seg))
        }
    }
}
