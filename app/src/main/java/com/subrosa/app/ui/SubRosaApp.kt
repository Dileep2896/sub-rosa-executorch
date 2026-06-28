package com.subrosa.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subrosa.app.data.demo.DemoSeeder
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.SessionPhase
import com.subrosa.app.ui.capture.CaptureScreen
import com.subrosa.app.ui.cases.CaseProfileScreen
import com.subrosa.app.ui.client.ClientProfileScreen
import com.subrosa.app.ui.client.NewClientScreen
import com.subrosa.app.ui.consent.ConsentScreen
import com.subrosa.app.ui.home.HomeScreen
import com.subrosa.app.ui.matter.MatterTypeScreen
import com.subrosa.app.ui.panels.MetricsPanel
import com.subrosa.app.ui.panels.PrivacyPanel
import com.subrosa.app.ui.processing.ProcessingScreen
import com.subrosa.app.ui.results.ResultsScreen
import com.subrosa.app.ui.theme.BrassSoft
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.PaperHigh
import com.subrosa.app.ui.theme.RuleLine
import com.subrosa.app.ui.theme.SealEmblem
import com.subrosa.app.ui.theme.SubRosaTheme
import com.subrosa.app.ui.theme.rememberDossierPaper

@Composable
fun SubRosaApp(vm: SessionViewModel) {
    SubRosaTheme {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val clients by vm.clients.collectAsStateWithLifecycle()
        val cases by vm.cases.collectAsStateWithLifecycle()
        val sessions by vm.sessions.collectAsStateWithLifecycle()
        val documents by vm.documents.collectAsStateWithLifecycle()
        val warmupReady by vm.warmupReady.collectAsStateWithLifecycle()
        val context = LocalContext.current
        // One-shot user messages (e.g. "Saved to Downloads") surface as a toast, then clear.
        LaunchedEffect(ui.message) {
            ui.message?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                vm.clearMessage()
            }
        }
        var showPanels by remember { mutableStateOf(false) }
        val paper = rememberDossierPaper()
        val matterName: (MatterType) -> String = { vm.checklistFor(it)?.displayName ?: it.name }

        // System back walks the Client → Case → Session hierarchy; at HOME it falls through to exit.
        BackHandler(enabled = ui.phase != SessionPhase.HOME) { vm.back() }

        Box(Modifier.fillMaxSize().then(paper)) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Masthead(
                        panelsShown = showPanels,
                        showBack = ui.phase != SessionPhase.HOME,
                        onBack = { vm.back() },
                        onHome = { vm.goHome() },
                        onToggle = { showPanels = !showPanels },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().imePadding()) {
                    AnimatedContent(
                        targetState = ui.phase,
                        transitionSpec = {
                            (fadeIn(tween(320)) + slideInHorizontally(tween(320)) { it / 14 }) togetherWith fadeOut(tween(200))
                        },
                        label = "phase",
                    ) { phase ->
                        when (phase) {
                            SessionPhase.HOME ->
                                HomeScreen(
                                    clients = clients,
                                    onNewClient = vm::startNewClient,
                                    onOpenClient = vm::openClient,
                                )

                            SessionPhase.NEW_CLIENT ->
                                NewClientScreen(
                                    initial = ui.editingClient,
                                    onSubmit = { name, phone, email, address, note ->
                                        if (ui.editingClient != null) {
                                            vm.updateClient(name, phone, email, address, note)
                                        } else {
                                            vm.createClient(name, phone, email, address, note)
                                        }
                                    },
                                    onCancel = { ui.editingClient?.let(vm::openClient) ?: vm.goHome() },
                                )

                            SessionPhase.CLIENT_PROFILE ->
                                ui.client?.let { client ->
                                    ClientProfileScreen(
                                        client = client,
                                        cases = cases.filter { it.clientId == client.id },
                                        matterName = matterName,
                                        onNewCase = vm::newCase,
                                        onOpenCase = vm::openCase,
                                        onEdit = vm::startEditClient,
                                        onDelete = { vm.deleteClient(client) },
                                    )
                                }

                            SessionPhase.NEW_CASE ->
                                MatterTypeScreen(checklists = vm.matterChecklists(), onPick = vm::createCase)

                            SessionPhase.CASE_PROFILE ->
                                ui.case?.let { case ->
                                    CaseProfileScreen(
                                        case = case,
                                        clientName = ui.client?.name ?: "Client",
                                        sessions = sessions.filter { it.caseId == case.id },
                                        documents = documents,
                                        matterName = matterName,
                                        onNewConsultation = vm::startNewConsultation,
                                        onOpenSession = vm::openSession,
                                        onAttach = vm::attachDocument,
                                        onOpenDoc = vm::openDocument,
                                        onDeleteDoc = vm::deleteDocument,
                                        onDeleteCase = { vm.deleteCase(case) },
                                        onExportReport = { vm.exportReport(case) },
                                        onDownloadReport = { vm.downloadReport(case) },
                                        onDeleteSession = vm::deleteSession,
                                    )
                                }

                            SessionPhase.CONSENT ->
                                ConsentScreen(onConsent = vm::onConsent)

                            SessionPhase.CAPTURE ->
                                CaptureScreen(
                                    ui = ui,
                                    warmupReady = warmupReady,
                                    onStart = vm::startRecording,
                                    onStop = vm::stopAndProcess,
                                    onLoadScript = if (ui.case?.id == DemoSeeder.CASE_ID) vm::loadScript else null,
                                )

                            SessionPhase.PROCESSING ->
                                ProcessingScreen(
                                    stage = vm.processingStage,
                                    metrics = vm.metrics,
                                    onCancel = vm::cancelProcessing,
                                )

                            SessionPhase.RESULTS ->
                                ResultsScreen(
                                    ui = ui,
                                    checklist = vm.checklistFor(ui.matterType),
                                    canResume = ui.canResume,
                                    onContinue = vm::continueConsultation,
                                    onSeal = vm::sealAndFinish,
                                    onHome = { ui.case?.let(vm::openCase) ?: vm.goHome() },
                                )
                        }
                    }

                    if (!warmupReady) {
                        WarmingBanner(Modifier.align(Alignment.TopCenter))
                    }
                    if (showPanels) {
                        ProofPanels(vm = vm, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    }
                }
            }
        }
    }
}

/** Shown at launch while the on-device models load, so nobody starts a real consultation cold. */
@Composable
private fun WarmingBanner(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "warm")
    val alpha by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "dot",
    )
    Surface(modifier = modifier.fillMaxWidth(), color = Ink, shadowElevation = 6.dp) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(BrassSoft.copy(alpha = alpha)))
            Text(
                "Preparing the on-device AI — one moment…",
                style = MaterialTheme.typography.labelMedium,
                color = PaperHigh,
            )
        }
    }
}

@Composable
private fun Masthead(
    panelsShown: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBack) {
                BackButton(onClick = onBack)
            }
            Row(
                Modifier.weight(1f).clickable(onClick = onHome),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SealEmblem(diameter = 26.dp)
                Text("SUB ROSA", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp), color = Ink)
            }
            TextButton(onClick = onToggle) {
                Text(if (panelsShown) "HIDE" else "PROOF", style = MaterialTheme.typography.labelMedium, color = if (panelsShown) InkSoft else Oxblood)
            }
        }
        RuleLine()
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Oxblood).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.60f, size.height * 0.16f)
                lineTo(size.width * 0.33f, size.height * 0.50f)
                lineTo(size.width * 0.60f, size.height * 0.84f)
            }
            drawPath(
                path = path,
                color = Color(0xFFF6EEDF),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun ProofPanels(vm: SessionViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PaperHigh,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Oxblood),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            PrivacyPanel(vm.privacy)
            Spacer(Modifier.height(18.dp))
            MetricsPanel(vm.metrics)
        }
    }
}
