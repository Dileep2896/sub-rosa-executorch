package com.subrosa.app.domain

import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/** Events emitted while audio is transcribed. */
sealed interface TranscriptionEvent {
    /** Optional in-progress text for a live preview. */
    data class Partial(val text: String) : TranscriptionEvent

    /** A finalized, speaker-attributed segment. [audio] is its 16 kHz mono float samples, for live speaker-id. */
    data class SegmentFinal(val segment: TranscriptSegment, val audio: FloatArray? = null) : TranscriptionEvent

    data class Error(val message: String) : TranscriptionEvent
}

/**
 * On-device speech-to-text. The whisper.cpp implementation arrives in Phase 2; Phase 1 uses
 * [com.subrosa.app.data.asr.ScriptedTranscriber], which replays the fixture transcript.
 *
 * The transcriber stays speaker-agnostic: [speakerProvider] is read at segment-finalization time so
 * the caller's tap UI (which owns "who is talking now") supplies the attribution out-of-band.
 */
interface Transcriber {
    fun transcribe(
        audio: Flow<ShortArray>,
        speakerProvider: () -> Speaker,
    ): Flow<TranscriptionEvent>
}
