package com.subrosa.app.di

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.subrosa.app.data.AssetReader
import com.subrosa.app.data.asr.AudioRecorder
import com.subrosa.app.data.asr.DiarSegment
import com.subrosa.app.data.asr.LiveSpeakerId
import com.subrosa.app.data.asr.OnnxDiarizer
import com.subrosa.app.data.asr.WhisperEngine
import com.subrosa.app.data.asr.WhisperTranscriber
import com.subrosa.app.data.cases.CaseStore
import com.subrosa.app.data.client.ClientStore
import com.subrosa.app.data.demo.DemoSeeder
import com.subrosa.app.data.docs.DocumentStore
import com.subrosa.app.data.checklist.AssetChecklistRepository
import com.subrosa.app.data.notes.ExecuTorchNotesGenerator
import com.subrosa.app.data.notes.PromptStyle
import com.subrosa.app.data.notes.SpeakerAttributor
import com.subrosa.app.data.report.CaseReport
import com.subrosa.app.data.report.CaseReportPdf
import com.subrosa.app.domain.ChecklistRepository
import com.subrosa.app.domain.model.Case
import com.subrosa.app.domain.model.Fact
import com.subrosa.app.data.session.SessionStore
import com.subrosa.app.domain.NotesGenerator
import com.subrosa.app.domain.Transcriber
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.NotesResult
import com.subrosa.app.domain.model.Transcript
import com.subrosa.app.llm.FallbackTextEngine
import com.subrosa.app.llm.LlmEngine
import com.subrosa.app.llm.NpuRunnerEngine
import com.subrosa.app.llm.TextEngine
import com.subrosa.app.metrics.MetricsCollector
import com.subrosa.app.metrics.PrivacyMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manual dependency container (no Hilt — hackathon speed). The app always runs the real on-device
 * engine ([notesGenerator] = ExecuTorch LLM) and [transcriber] = whisper. The scripted demo streams a
 * loaded transcript (see SessionViewModel.loadScript) through that same real pipeline.
 */
class AppContainer(app: Application) {

    private val appContext: Context = app
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val assets: AssetReader = AssetReader { name ->
        app.assets.open(name).bufferedReader().use { it.readText() }
    }

    val metrics = MetricsCollector()
    val privacyMonitor = PrivacyMonitor(app)
    val checklists: ChecklistRepository = AssetChecklistRepository(assets, json)
    val sessionStore = SessionStore(File(app.filesDir, "sessions"), json)
    val clientStore = ClientStore(File(app.filesDir, "clients"), json)
    val caseStore = CaseStore(File(app.filesDir, "cases"), json)
    val documentStore = DocumentStore(app, File(app.filesDir, "documents"))

    /**
     * Where on-device model files live. We use the app's *external* files dir (not /data/local/tmp,
     * which SELinux blocks an untrusted app from reading) so a plain `adb push` lands them somewhere
     * the app can read with no root and no storage permission. Push the .pte as model.pte and the
     * tokenizer as tokenizer.bin:
     *   adb push model.pte     /sdcard/Android/data/com.subrosa.app/files/llama/model.pte
     *   adb push tokenizer.bin /sdcard/Android/data/com.subrosa.app/files/llama/tokenizer.bin
     */
    val modelDir: File = File(app.getExternalFilesDir(null), "llama")

    /** True when a QNN-delegated model is present → the LLM runs on the Hexagon NPU instead of CPU/XNNPACK. */
    val llmOnNpu: Boolean get() = File(modelDir, "model-qnn.pte").exists()

    /** CPU/XNNPACK engine over model.pte — the always-available path (and the NPU fallback). */
    private fun buildCpuEngine(): LlmEngine = LlmEngine(
        modelPath = File(modelDir, "model.pte").absolutePath,
        tokenizerPath = File(modelDir, "tokenizer.bin").absolutePath,
        metrics = metrics,
        backendLabel = "CPU · XNNPACK",
    )

    /**
     * The active text engine. With a QNN model present, the LLM runs on the Hexagon NPU via the
     * qnn_llama_runner subprocess ([NpuRunnerEngine]) — which speaks model-qnn.pte's `kv_forward` +
     * quantized-KV-cache contract that the generic LlmModule cannot — wrapped in a [FallbackTextEngine]
     * that drops to CPU/XNNPACK (red "CPU FALLBACK") on any DSP failure. Otherwise model.pte runs on CPU.
     */
    val llmEngine: TextEngine by lazy {
        if (llmOnNpu) {
            Log.i("LlmEngine", "backend=QNN/Hexagon-NPU (qnn_llama_runner) model=model-qnn.pte")
            metrics.setBackend("Hexagon NPU · QNN")
            val npu = NpuRunnerEngine(
                context = appContext,
                modelPath = File(modelDir, "model-qnn.pte").absolutePath,
                tokenizerPath = File(modelDir, "tokenizer.bin").absolutePath,
                metrics = metrics,
            )
            FallbackTextEngine(primary = npu, fallbackProvider = ::buildCpuEngine, metrics = metrics)
        } else {
            Log.i("LlmEngine", "backend=XNNPACK/CPU model=model.pte")
            buildCpuEngine().also { metrics.setBackend("CPU · XNNPACK") }
        }
    }

    // CHATML for Qwen3; switch to LLAMA3 when the Llama 3.2 .pte lands. The app always uses this.
    val notesGenerator: NotesGenerator by lazy {
        ExecuTorchNotesGenerator(llmEngine, promptStyle = PromptStyle.CHATML)
    }

    /** Content-based speaker attribution (lawyer asks / client narrates) — replaces acoustic diarization. */
    val speakerAttributor: SpeakerAttributor by lazy {
        SpeakerAttributor(llmEngine, promptStyle = PromptStyle.CHATML)
    }

    /** Warm the model (load + a tiny generation) to hide cold-load latency before the first consultation. */
    suspend fun warmupEngine(): String = llmEngine.warmup()

    /** Diagnostic: run the real notes pipeline on the bundled sample transcript (logs the result). */
    suspend fun notesSelfTest(): NotesResult {
        val transcript = json.decodeFromString<Transcript>(assets.read("sample_transcript.json"))
        val checklist = checklists.forType(MatterType.CONTRACT_DISPUTE)
        val result = notesGenerator.generate(transcript, checklist)
        val summary = when (result) {
            is NotesResult.Parsed -> "PARSED facts=${result.notes.facts.size} missing=${result.notes.missing.size} prompts=${result.notes.prompts.size}"
            is NotesResult.Partial -> "PARTIAL facts=${result.notes.facts.size} missing=${result.notes.missing.size} errors=${result.errors.size}"
            is NotesResult.Failed -> "FAILED ${result.reason}"
            is NotesResult.Insufficient -> "INSUFFICIENT ${result.reason}"
        }
        Log.i("NotesSelfTest", "SUMMARY $summary")
        Log.i("NotesSelfTest", "FULL $result")
        return result
    }

    // ── Case report (PDF, rendered on-device) ──────────────────────────────────
    val caseReportPdf by lazy { CaseReportPdf(appContext) }

    /** Diagnostic: render a sample themed report to the external dir so it can be pulled + viewed. */
    fun reportSelfTest(): File {
        val sample = CaseReport(
            clientName = "Maya Okonkwo",
            contact = listOf(
                "Phone" to "(415) 555-0182",
                "Email" to "maya@okonkwobakery.com",
                "Address" to "1190 Valencia St, San Francisco, CA",
            ),
            matter = "Contract dispute",
            caseTitle = "Okonkwo — Trellis Studio website contract",
            facts = listOf(
                Fact("The client hired Trellis Studio to build a website for her bakery, and it was never finished.", "I hired a company called Trellis Studio to build a website for my bakery, and they never finished it.", true),
                Fact("They agreed Trellis would deliver the finished website for four thousand dollars; the client paid two thousand up front as a deposit.", "We agreed they would deliver the finished website for four thousand dollars, and I paid two thousand dollars up front as a deposit.", true),
                Fact("It was due last Friday, but Trellis stopped responding.", "They were supposed to have it done by last Friday, but they just stopped responding to me.", true),
                Fact("The client kept the emails but cannot find the deposit invoice.", "I've got all our emails saved, but I cannot find the invoice for my deposit anywhere.", true),
            ),
            missing = listOf(
                "What each party promised (consideration)",
                "Dates of the breach or non-performance",
                "Financial loss or damages claimed",
                "Whether the client tried to resolve it / mitigate",
                "Any documents or evidence (invoices, emails, signed copy)",
                "Any witnesses",
            ),
            prompts = listOf(
                "Have you tried to resolve this directly with Trellis Studio?",
                "Do you have a signed copy of the agreement, or was it all over email?",
            ),
            documents = listOf("deposit-screenshot.png · added 18 Jun 2026", "email-thread.pdf · added 20 Jun 2026"),
            consultations = listOf(
                "Consultation 1 · 18 Jun 2026 · SEALED · 7 facts",
                "Consultation 2 · 22 Jun 2026 · in progress · 4 facts",
            ),
            generatedAtMs = System.currentTimeMillis(),
        )
        val out = File(appContext.getExternalFilesDir(null), "sample-report.pdf")
        return caseReportPdf.render(sample, out)
    }

    /** Aggregate a case's client + consultations + documents into a render-ready [CaseReport]. */
    fun buildCaseReport(case: Case): CaseReport {
        val client = runCatching { clientStore.get(case.clientId) }.getOrNull()
        val sessions = runCatching { sessionStore.list() }.getOrDefault(emptyList())
            .filter { it.caseId == case.id }.sortedBy { it.createdAtMs }
        val docs = runCatching { documentStore.list(case.id) }.getOrDefault(emptyList())
        val notesList = sessions.mapNotNull { it.notes }

        val facts = notesList.flatMap { it.facts }.distinctBy { it.statement.trim().lowercase() }
        // An item is still outstanding only if every consultation left it uncovered.
        val missing = if (notesList.isEmpty()) emptyList() else {
            val common = notesList.map { it.missing.toSet() }.reduce { a, b -> a intersect b }
            notesList.last().missing.filter { it in common }
        }
        val prompts = notesList.flatMap { it.prompts }.distinct()

        val contact = buildList {
            client?.phone?.takeIf { it.isNotBlank() }?.let { add("Phone" to it) }
            client?.email?.takeIf { it.isNotBlank() }?.let { add("Email" to it) }
            client?.address?.takeIf { it.isNotBlank() }?.let { add("Address" to it) }
        }
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.US)
        return CaseReport(
            clientName = client?.name ?: "—",
            contact = contact,
            matter = checklists.forType(case.matterType).displayName,
            caseTitle = case.title,
            facts = facts,
            missing = missing,
            prompts = prompts,
            documents = docs.map { "${it.displayName} · added ${fmt.format(Date(it.addedAtMs))}" },
            consultations = sessions.mapIndexed { i, s ->
                "Consultation ${i + 1} · ${fmt.format(Date(s.createdAtMs))} · ${s.status} · ${s.notes?.facts?.size ?: 0} facts"
            },
            generatedAtMs = System.currentTimeMillis(),
        )
    }

    /** Render a case report PDF (to ephemeral cache) and hand it to the system share sheet. */
    suspend fun shareCaseReport(case: Case) = withContext(Dispatchers.Default) {
        val report = buildCaseReport(case)
        val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
        val out = caseReportPdf.render(report, File(dir, reportFileName(report, case)))
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sub Rosa — ${report.clientName} · ${case.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(
            Intent.createChooser(send, "Export case report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Render the case report PDF straight into the phone's public Downloads folder via MediaStore.
     * Needs no storage permission on API 29+ (minSdk is 31) and never touches the network. Returns the
     * saved file name so the UI can confirm it.
     */
    suspend fun saveReportToDownloads(case: Case): String = withContext(Dispatchers.IO) {
        val report = buildCaseReport(case)
        val name = reportFileName(report, case)
        val tmp = caseReportPdf.render(report, File(appContext.cacheDir, name))
        val resolver = appContext.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error("Could not create a Downloads entry")
        resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            ?: error("Could not open the Downloads file for writing")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        name
    }

    /** Human-readable PDF name: SubRosa_<Client>_<Case>.pdf, so a download clearly names its case. */
    private fun reportFileName(report: CaseReport, case: Case): String {
        fun slug(s: String, max: Int) = s.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(max)
        val base = listOf(slug(report.clientName, 24), slug(case.title, 36))
            .filter { it.isNotBlank() }.joinToString("_").ifBlank { "case" }
        return "SubRosa_$base.pdf"
    }

    /** whisper.cpp ASR. Model + self-test sample pushed alongside the LLM model in [modelDir]. */
    val whisperEngine: WhisperEngine by lazy {
        WhisperEngine(File(modelDir, "whisper-model.bin").absolutePath, metrics)
    }
    /** The live on-device transcriber — always whisper. */
    val transcriber: Transcriber by lazy { WhisperTranscriber(whisperEngine) }

    /** On-device speaker diarization (sherpa-onnx). Models pushed alongside the LLM/whisper models. */
    val diarizer by lazy {
        OnnxDiarizer(
            segmentationModelPath = File(modelDir, "diarize-segmentation.onnx").absolutePath,
            embeddingModelPath = File(modelDir, "diarize-embedding.onnx").absolutePath,
        )
    }

    /** Live per-segment speaker id (sherpa-onnx voice embeddings) — the running-transcript counterpart to [diarizer]. */
    val liveSpeakerId by lazy {
        LiveSpeakerId(embeddingModelPath = File(modelDir, "diarize-embedding.onnx").absolutePath)
    }

    /** Diagnostic: diarize a known 2-speaker clip on-device — proves sherpa-onnx loads + runs on the S25. */
    suspend fun diarizerSelfTest(): String = withContext(Dispatchers.Default) {
        if (!diarizer.available) return@withContext "diarizer models not present"
        val wav = File(modelDir, "diarize-test.wav")
        if (!wav.exists()) return@withContext "no diarize-test.wav present"
        val floats = WhisperEngine.decodeWavToMonoFloats(wav)
        val segs = diarizer.diarize(floats, numSpeakers = 2)
        val speakers = segs.map { it.speaker }.distinct().sorted()
        val summary = "diarize-test (${floats.size / 16000}s) -> ${segs.size} segments across speakers $speakers"
        Log.i("DiarizerSelfTest", summary)
        segs.take(14).forEach { Log.i("DiarizerSelfTest", "  spk${it.speaker}  ${"%.1f".format(it.startSec)}-${"%.1f".format(it.endSec)}s") }
        summary
    }

    /**
     * Diarize the full recorded PCM (16 kHz mono chunks) into speaker turns. Returns [] when the diarizer
     * models aren't present, the clip is under a second, or diarization fails — so the caller falls back
     * to the lawyer's manual speaker taps and never blocks.
     */
    suspend fun diarize(pcmChunks: List<ShortArray>, numSpeakers: Int): List<DiarSegment> =
        withContext(Dispatchers.Default) {
            if (!diarizer.available) return@withContext emptyList()
            val total = pcmChunks.sumOf { it.size }
            if (total < 16_000) return@withContext emptyList() // < 1 s — nothing to separate
            val merged = ShortArray(total)
            var off = 0
            for (c in pcmChunks) { c.copyInto(merged, off); off += c.size }
            diarizer.diarize(WhisperEngine.pcm16ToFloats(merged), numSpeakers)
        }

    /** Diagnostic: live-assign each turn-aligned segment of the 2-speaker clip + compare to the diarizer — proves embeddings differentiate clean turns. */
    suspend fun liveSpeakerIdSelfTest(): String = withContext(Dispatchers.Default) {
        if (!liveSpeakerId.available || !diarizer.available) return@withContext "diarization models not present"
        val wav = File(modelDir, "diarize-test.wav")
        if (!wav.exists()) return@withContext "no diarize-test.wav present"
        val floats = WhisperEngine.decodeWavToMonoFloats(wav)
        val turns = diarizer.diarize(floats, 2) // turn boundaries (ground-truth-ish)
        liveSpeakerId.reset()
        val live = turns.map { t ->
            val s = (t.startSec * 16_000).toInt().coerceIn(0, floats.size)
            val e = (t.endSec * 16_000).toInt().coerceIn(s, floats.size)
            if (e - s < 8_000) -1 else liveSpeakerId.assign(floats.copyOfRange(s, e), 2)
        }
        val distinct = live.filter { it >= 0 }.distinct().size
        val summary = "turn-aligned: diar=${turns.map { it.speaker }} live=$live ($distinct distinct voices)"
        Log.i("LiveSpkSelfTest", summary)
        summary
    }

    /** Real microphone source for live capture. */
    val audioRecorder = AudioRecorder()

    // ── Scripted demo (real LLM over a loaded transcript) ──
    private val demoSeeder by lazy { DemoSeeder(clientStore, caseStore) }

    /** Seed the demo client + case (Maya / Trellis), idempotent. Returns the case. */
    fun seedDemo(): Case = demoSeeder.seed(System.currentTimeMillis())

    /** Warm + self-test the ASR model by transcribing a known sample — proves whisper runs on-device. */
    suspend fun warmupTranscriber(): String {
        whisperEngine.load() // load only — never block the single-threaded engine transcribing a sample
        return "whisper loaded"
    }

}
