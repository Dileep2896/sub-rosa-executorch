package com.subrosa.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.subrosa.app.data.docs.DocumentRef
import com.subrosa.app.data.session.SavedSession
import com.subrosa.app.data.demo.DemoSeeder
import com.subrosa.app.data.demo.DemoTranscriber
import com.subrosa.app.di.AppContainer
import com.subrosa.app.domain.TranscriptionEvent
import com.subrosa.app.domain.model.CaptureMode
import com.subrosa.app.domain.model.Case
import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.Client
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.NotesResult
import com.subrosa.app.domain.model.SessionPhase
import com.subrosa.app.domain.model.SessionStatus
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.domain.model.TranscriptSegment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionUiState(
    val phase: SessionPhase = SessionPhase.HOME,
    val consentGiven: Boolean = false,
    val client: Client? = null,
    val case: Case? = null,
    val editingClient: Client? = null,
    val sessionId: String? = null,
    val sessionCreatedAtMs: Long = 0L,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val matterType: MatterType? = null,
    val isRecording: Boolean = false,
    val currentSpeaker: Speaker = Speaker.LAWYER,
    val captureMode: CaptureMode = CaptureMode.AUTO_ASSIST,
    val segments: List<TranscriptSegment> = emptyList(),
    val partialText: String = "",
    val notesResult: NotesResult? = null,
    val errorMessage: String? = null,
    /** True while a scripted demo consultation is loaded — keeps the script's speaker labels (skips LLM attribution). */
    val scripted: Boolean = false,
    /** One-shot, transient user message (e.g. a download confirmation) shown then cleared. */
    val message: String? = null,
    /** "People in the room" — seeds the diarizer's cluster count at Stop. */
    val speakerCount: Int = 2,
) {
    val canResume: Boolean get() = status == SessionStatus.IN_PROGRESS
}

/** Below this many words a consultation is too thin to summarize at all (see [SessionViewModel]). */
private const val MIN_TOTAL_WORDS = 12

/** Coarse stages of the post-recording pipeline, surfaced to the processing screen as it works. */
enum class ProcessingStage { PREPARING, ATTRIBUTING, REASONING, DONE }

/**
 * Owns the whole flow. Work is organized **Client → Case → Session**: a client has cases (matters),
 * each case has its own documents and its own living, resumable consultations. Everything is local
 * and encrypted at rest.
 */
class SessionViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(SessionUiState())
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    val metrics = container.metrics.state
    val privacy = container.privacyMonitor.state

    /** False until both on-device models finish warming at launch — gates starting a real consultation. */
    private val _warmupReady = MutableStateFlow(false)
    val warmupReady: StateFlow<Boolean> = _warmupReady.asStateFlow()

    /** Coarse pipeline stage during PROCESSING, for the working-screen animation. */
    private val _processingStage = MutableStateFlow(ProcessingStage.PREPARING)
    val processingStage: StateFlow<ProcessingStage> = _processingStage.asStateFlow()

    init {
        // Seed the demo client (Maya / Trellis) so it's always selectable. Runs before _clients/_cases
        // initialize below, so they pick it up. Idempotent (fixed ids).
        runCatching { container.seedDemo() }
        // Prove the OS blocks outbound networking (no INTERNET permission) — populates the privacy probe.
        viewModelScope.launch { runCatching { container.privacyMonitor.probe() } }
        // Warm the real on-device models in the background; flip ready when both are loaded.
        viewModelScope.launch {
            runCatching { container.warmupTranscriber() } // whisper (~0.1s)
            runCatching { container.warmupEngine() }       // Qwen3 LLM (~3s)
            _warmupReady.value = true
        }
    }

    /** Render the case report PDF on-device and open the system share sheet. */
    fun exportReport(case: Case) {
        viewModelScope.launch { runCatching { container.shareCaseReport(case) } }
    }

    /** Render the case report PDF straight into the phone's Downloads folder (offline, no permission). */
    fun downloadReport(case: Case) {
        viewModelScope.launch {
            val name = runCatching { container.saveReportToDownloads(case) }.getOrNull()
            _ui.update {
                it.copy(message = name?.let { n -> "Saved to Downloads · $n" } ?: "Couldn't save the report.")
            }
        }
    }

    /** Clear a one-shot UI message after it's been shown (e.g. the download confirmation toast). */
    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** Set how many people are in the room — seeds the diarizer at Stop (clamped 1..6). */
    fun setSpeakerCount(n: Int) = _ui.update { it.copy(speakerCount = n.coerceIn(1, 6)) }

    // ── Scripted demo (real LLM over a loaded transcript) ────────────────────────
    /**
     * Stream a scripted consultation into the live transcript, then let the REAL pipeline summarize it.
     * The first pass streams the opening script (clearing any live-mic teaser); a continuation assembles
     * the follow-up from the report's ACTUAL missing items, so it always fills exactly what the model
     * flagged — never a guess that can drift out of sync. The presenter then taps Stop & seal as usual.
     */
    fun loadScript() {
        if (_ui.value.isRecording) return
        captureJob?.cancel()
        val prior = _ui.value.notesResult?.notesOrNull
        val script = if (prior == null) {
            DemoSeeder.openingScript()
        } else {
            val frags = prior.missing.flatMap { DemoSeeder.followUpFor(it) }
            Transcript(frags.ifEmpty { DemoSeeder.defaultFollowUp() })
        }
        container.privacyMonitor.refresh()
        _ui.update {
            it.copy(
                isRecording = true,
                partialText = "",
                scripted = true,
                segments = if (prior == null) emptyList() else it.segments, // first pass: clear the teaser
            )
        }
        captureJob = DemoTranscriber(transcript = script, spanMs = 9_000L, minMs = 520L, maxMs = 2_600L)
            .transcribe(audio = emptyFlow(), speakerProvider = { liveSpeaker })
            .onEach(::reduceEvent)
            .launchIn(viewModelScope)
    }

    private val _clients = MutableStateFlow(loadClients())
    val clients: StateFlow<List<Client>> = _clients.asStateFlow()

    private val _cases = MutableStateFlow(loadCases())
    val cases: StateFlow<List<Case>> = _cases.asStateFlow()

    private val _sessions = MutableStateFlow(loadSessions())
    val sessions: StateFlow<List<SavedSession>> = _sessions.asStateFlow()

    private val _documents = MutableStateFlow<List<DocumentRef>>(emptyList())
    val documents: StateFlow<List<DocumentRef>> = _documents.asStateFlow()

    fun matterChecklists(): List<Checklist> = container.checklists.all()
    fun checklistFor(type: MatterType?): Checklist? = type?.let { container.checklists.forType(it) }

    @Volatile
    private var liveSpeaker: Speaker = Speaker.LAWYER
    private var captureJob: Job? = null
    private var processingJob: Job? = null

    private fun loadClients() = runCatching { container.clientStore.list() }.getOrDefault(emptyList())
    private fun loadCases() = runCatching { container.caseStore.list() }.getOrDefault(emptyList())
    private fun loadSessions() = runCatching { container.sessionStore.list() }.getOrDefault(emptyList())
    fun refreshClients() { _clients.value = loadClients() }
    fun refreshCases() { _cases.value = loadCases() }
    fun refreshSessions() { _sessions.value = loadSessions() }
    private fun refreshDocuments() {
        _documents.value = _ui.value.case?.let {
            runCatching { container.documentStore.list(it.id) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    // ── Clients / home ────────────────────────────────────────────────────────
    fun goHome() {
        captureJob?.cancel(); processingJob?.cancel()
        refreshClients(); refreshCases(); refreshSessions()
        _ui.update { it.copy(phase = SessionPhase.HOME, client = null, case = null) }
    }

    /** Up-navigation that follows the Client → Case → Session hierarchy (masthead + system back). */
    fun back() {
        val s = _ui.value
        when (s.phase) {
            SessionPhase.RESULTS -> s.case?.let(::openCase) ?: goHome()
            SessionPhase.PROCESSING -> cancelProcessing()
            SessionPhase.CAPTURE -> { captureJob?.cancel(); s.case?.let(::openCase) ?: goHome() }
            SessionPhase.CONSENT -> s.case?.let(::openCase) ?: goHome()
            SessionPhase.NEW_CASE -> s.client?.let(::openClient) ?: goHome()
            SessionPhase.CASE_PROFILE -> s.client?.let(::openClient) ?: goHome()
            SessionPhase.NEW_CLIENT -> s.editingClient?.let(::openClient) ?: goHome()
            SessionPhase.CLIENT_PROFILE -> goHome()
            SessionPhase.HOME -> Unit
        }
    }

    fun startNewClient() = _ui.update { it.copy(phase = SessionPhase.NEW_CLIENT, editingClient = null) }

    fun startEditClient() = _ui.update { it.copy(phase = SessionPhase.NEW_CLIENT, editingClient = it.client) }

    private fun cleanField(s: String?) = s?.trim()?.ifEmpty { null }

    fun createClient(name: String, phone: String?, email: String?, address: String?, note: String?) {
        val now = System.currentTimeMillis()
        val client = Client(
            id = now.toString(),
            name = name.trim().ifEmpty { "Unnamed client" },
            phone = cleanField(phone),
            email = cleanField(email),
            address = cleanField(address),
            note = cleanField(note),
            createdAtMs = now,
        )
        container.clientStore.save(client)
        refreshClients()
        openClient(client)
    }

    fun updateClient(name: String, phone: String?, email: String?, address: String?, note: String?) {
        val base = _ui.value.editingClient ?: return
        val updated = base.copy(
            name = name.trim().ifEmpty { base.name },
            phone = cleanField(phone),
            email = cleanField(email),
            address = cleanField(address),
            note = cleanField(note),
        )
        container.clientStore.save(updated)
        refreshClients()
        _ui.update { it.copy(editingClient = null, client = updated, phase = SessionPhase.CLIENT_PROFILE) }
    }

    fun openClient(client: Client) {
        _ui.update { it.copy(phase = SessionPhase.CLIENT_PROFILE, client = client, case = null) }
        refreshCases()
        refreshSessions()
    }

    // ── Cases ─────────────────────────────────────────────────────────────────
    fun newCase() = _ui.update { it.copy(phase = SessionPhase.NEW_CASE) }

    fun createCase(matterType: MatterType) {
        val client = _ui.value.client ?: return
        val now = System.currentTimeMillis()
        val case = Case(
            id = now.toString(),
            clientId = client.id,
            matterType = matterType,
            title = container.checklists.forType(matterType).displayName,
            createdAtMs = now,
        )
        container.caseStore.save(case)
        refreshCases()
        openCase(case)
    }

    fun openCase(case: Case) {
        _ui.update { it.copy(phase = SessionPhase.CASE_PROFILE, case = case) }
        refreshSessions()
        refreshDocuments()
    }

    // ── Consultation (within the current case) ──────────────────────────────────
    fun startNewConsultation() {
        val case = _ui.value.case ?: return
        captureJob?.cancel(); processingJob?.cancel()
        liveSpeaker = Speaker.LAWYER
        container.metrics.reset()
        val now = System.currentTimeMillis()
        _ui.update {
            it.copy(
                phase = SessionPhase.CONSENT,
                consentGiven = false,
                sessionId = now.toString(),
                sessionCreatedAtMs = now,
                status = SessionStatus.IN_PROGRESS,
                matterType = case.matterType,
                isRecording = false,
                currentSpeaker = Speaker.LAWYER,
                captureMode = CaptureMode.AUTO_ASSIST,
                segments = emptyList(),
                partialText = "",
                notesResult = null,
                scripted = false,
            )
        }
    }

    fun onConsent() = _ui.update { it.copy(consentGiven = true, phase = SessionPhase.CAPTURE) }

    fun toggleSpeaker() {
        liveSpeaker = if (liveSpeaker == Speaker.LAWYER) Speaker.CLIENT else Speaker.LAWYER
        _ui.update { it.copy(currentSpeaker = liveSpeaker) }
    }

    fun setCaptureMode(mode: CaptureMode) = _ui.update { it.copy(captureMode = mode) }

    fun correctSpeaker(index: Int) = _ui.update { s ->
        if (index !in s.segments.indices) return@update s
        val seg = s.segments[index]
        val flipped = seg.copy(speaker = if (seg.speaker == Speaker.CLIENT) Speaker.LAWYER else Speaker.CLIENT)
        s.copy(segments = s.segments.toMutableList().also { it[index] = flipped })
    }

    fun startRecording() {
        if (_ui.value.isRecording) return
        _ui.update { it.copy(isRecording = true, partialText = "", scripted = false) } // live mic, append
        container.privacyMonitor.refresh()
        // The real on-device transcriber (whisper) reads the live mic; speakers are attributed from
        // content (live heuristic + an LLM pass at Stop), so no raw-audio buffer is kept.
        captureJob = container.transcriber
            .transcribe(audio = container.audioRecorder.stream(), speakerProvider = { liveSpeaker })
            .onEach { handleCaptureEvent(it) }
            .launchIn(viewModelScope)
    }

    fun stopAndProcess() {
        captureJob?.cancel()
        container.metrics.startGeneration()
        _processingStage.value = ProcessingStage.PREPARING
        _ui.update { it.copy(isRecording = false, phase = SessionPhase.PROCESSING) }
        processingJob = viewModelScope.launch {
            _processingStage.value = ProcessingStage.ATTRIBUTING
            // A scripted demo already carries correct speaker labels — trust them; else attribute by content.
            if (!_ui.value.scripted) runCatching { attributeSpeakers() }
            _processingStage.value = ProcessingStage.REASONING
            generateNotes()
            _processingStage.value = ProcessingStage.DONE
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        _ui.update { it.copy(phase = SessionPhase.CAPTURE) }
    }

    fun continueConsultation() = _ui.update { it.copy(phase = SessionPhase.CAPTURE) }

    fun sealAndFinish() {
        persist(_ui.value, SessionStatus.SEALED)
        refreshSessions()
        _ui.update { it.copy(status = SessionStatus.SEALED, phase = SessionPhase.CASE_PROFILE) }
    }

    fun openSession(saved: SavedSession) {
        val case = saved.caseId?.let { container.caseStore.get(it) } ?: _ui.value.case
        val client = saved.clientId?.let { container.clientStore.get(it) }
            ?: case?.clientId?.let { container.clientStore.get(it) }
            ?: _ui.value.client
        container.metrics.reset()
        _ui.update {
            it.copy(
                phase = SessionPhase.RESULTS,
                client = client,
                case = case,
                sessionId = saved.id,
                sessionCreatedAtMs = saved.createdAtMs,
                status = saved.status,
                matterType = saved.matterType,
                segments = saved.transcript.segments,
                notesResult = saved.notes?.let { n -> NotesResult.Parsed(n) },
            )
        }
    }

    // ── Documents (scoped to the current case) ──────────────────────────────────
    fun attachDocument(uri: Uri) {
        val case = _ui.value.case ?: return
        container.documentStore.add(case.id, uri)
        refreshDocuments()
    }

    fun deleteDocument(doc: DocumentRef) {
        container.documentStore.delete(doc)
        refreshDocuments()
    }

    fun openDocument(doc: DocumentRef) = container.documentStore.open(doc)

    // ── Delete (with cascade) ───────────────────────────────────────────────────
    fun deleteSession(saved: SavedSession) {
        container.sessionStore.delete(saved.id)
        refreshSessions()
    }

    private fun deleteCaseData(case: Case) {
        _sessions.value.filter { it.caseId == case.id }.forEach { container.sessionStore.delete(it.id) }
        container.documentStore.deleteAll(case.id)
        container.caseStore.delete(case.id)
    }

    fun deleteCase(case: Case) {
        deleteCaseData(case)
        refreshCases(); refreshSessions()
        _ui.value.client?.let(::openClient) ?: goHome()
    }

    fun deleteClient(client: Client) {
        _cases.value.filter { it.clientId == client.id }.forEach { deleteCaseData(it) }
        container.clientStore.delete(client.id)
        refreshClients(); refreshCases(); refreshSessions()
        goHome()
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /**
     * Live capture handler. A finalized line is appended immediately with an instant content-based label:
     * questions read as the lawyer, statements as the client. The LLM makes this authoritative over the
     * whole transcript at Stop ([attributeSpeakers]).
     */
    private suspend fun handleCaptureEvent(event: TranscriptionEvent) {
        if (event !is TranscriptionEvent.SegmentFinal) { reduceEvent(event); return }
        val seg = event.segment
        val speaker = container.speakerAttributor.heuristic(seg.text)
        _ui.update { it.copy(segments = it.segments + seg.copy(speaker = speaker), partialText = "") }
    }

    private fun reduceEvent(event: TranscriptionEvent) = _ui.update { s ->
        when (event) {
            is TranscriptionEvent.SegmentFinal -> {
                val seg = if (s.captureMode == CaptureMode.MANUAL) {
                    event.segment.copy(speaker = liveSpeaker)
                } else {
                    event.segment
                }
                s.copy(segments = s.segments + seg, partialText = "")
            }
            is TranscriptionEvent.Partial -> s.copy(partialText = event.text)
            is TranscriptionEvent.Error -> s.copy(errorMessage = event.message)
        }
    }

    private suspend fun generateNotes() {
        val state = _ui.value
        val type = state.matterType ?: return
        val checklist = container.checklists.forType(type)
        val transcript = Transcript(state.segments)
        // When resuming (Continue consultation), this is the previous pass's report — we merge into it.
        val priorNotes = state.notesResult?.notesOrNull

        // Edge-case guard: never fabricate a report from a near-empty consultation.
        // We surface a clear notice (and don't persist) instead of asking the model to summarize nothing.
        insufficiencyReason(transcript)?.let { reason ->
            _ui.update { it.copy(notesResult = NotesResult.Insufficient(reason), phase = SessionPhase.RESULTS) }
            return
        }

        val result = runCatching { container.notesGenerator.generate(transcript, checklist) }
            .getOrElse { NotesResult.Failed(rawOutput = "", reason = it.message ?: "engine error") }
        // Continued consultation → merge with the first report so nothing learned is lost.
        val finalResult = if (priorNotes != null) mergeWithPrior(priorNotes, result) else result
        container.privacyMonitor.refresh()
        _ui.update { it.copy(notesResult = finalResult, phase = SessionPhase.RESULTS) }
        persist(_ui.value, SessionStatus.IN_PROGRESS)
        refreshSessions()
    }

    /**
     * Combine the previous report ([prior]) with the freshly generated [current] one after a continued
     * consultation. Facts are unioned (deduped by statement, so a fact learned in the first pass is never
     * lost); the missing-items list is taken from the fresh pass — recomputed over the FULL transcript, so
     * items covered in the continuation drop off; follow-up prompts are unioned. If the fresh pass produced
     * no usable notes, the prior report is kept intact.
     */
    private fun mergeWithPrior(prior: Notes, current: NotesResult): NotesResult {
        val cur = current.notesOrNull ?: return NotesResult.Parsed(prior)
        val facts = (prior.facts + cur.facts).distinctBy { it.statement.trim().lowercase() }
        val merged = Notes(
            facts = facts,
            missing = cur.missing,
            prompts = (prior.prompts + cur.prompts).distinct(),
        )
        return when (current) {
            is NotesResult.Partial -> NotesResult.Partial(merged, current.errors)
            else -> NotesResult.Parsed(merged)
        }
    }

    /**
     * Post-recording speaker attribution: ask the LLM to label every transcript line LAWYER/CLIENT from
     * what was said (the lawyer guides/asks, the client narrates). Replaces acoustic diarization, which was
     * unreliable on similar voices through one mic. A no-op (keeps the live heuristic labels) on an empty
     * transcript, a count mismatch, or any model/parse failure.
     */
    private suspend fun attributeSpeakers() {
        val segs = _ui.value.segments
        if (segs.isEmpty()) return
        val labels = container.speakerAttributor.attribute(segs)
        if (labels.size != segs.size) return
        _ui.update { it.copy(segments = segs.mapIndexed { i, s -> s.copy(speaker = labels[i]) }) }
    }

    /**
     * A human reason if [transcript] is genuinely too thin to summarize (so we block the report), else null.
     * We only refuse the empty / a-handful-of-words case — a report is never invented from nothing. Short
     * but real consultations are allowed through; speaker labels come from content attribution, not taps.
     */
    private fun insufficiencyReason(transcript: Transcript): String? {
        fun wordCount(s: String) = s.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val totalWords = transcript.segments.sumOf { wordCount(it.text) }
        return when {
            totalWords == 0 ->
                "Nothing was captured yet. Record the consultation, then process again."
            totalWords < MIN_TOTAL_WORDS ->
                "Only a few words were captured — too little to produce reliable intake notes. Record a bit more, then process again."
            else -> null
        }
    }

    private fun persist(state: SessionUiState, status: SessionStatus) {
        val id = state.sessionId ?: return
        val type = state.matterType ?: return
        runCatching {
            container.sessionStore.save(
                SavedSession(
                    id = id,
                    clientId = state.client?.id,
                    caseId = state.case?.id,
                    matterType = type,
                    transcript = Transcript(state.segments),
                    notes = state.notesResult?.notesOrNull,
                    status = status,
                    createdAtMs = state.sessionCreatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(container) as T
            }
    }
}
