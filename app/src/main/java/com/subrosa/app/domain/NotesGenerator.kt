package com.subrosa.app.domain

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.NotesResult
import com.subrosa.app.domain.model.Transcript

/**
 * Produces structured intake notes from a transcript + matter-type checklist.
 *
 * The Phase-1 [com.subrosa.app.data.notes.FakeNotesGenerator] and the Phase-2
 * `ExecuTorchNotesGenerator` both implement this; [com.subrosa.app.di.AppContainer] swaps between
 * them at runtime. Implementations must NOT throw on bad model output — return [NotesResult.Failed].
 */
interface NotesGenerator {
    suspend fun generate(transcript: Transcript, checklist: Checklist): NotesResult
}
