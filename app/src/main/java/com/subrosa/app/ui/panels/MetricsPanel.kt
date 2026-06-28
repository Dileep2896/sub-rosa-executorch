package com.subrosa.app.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subrosa.app.metrics.Metrics
import com.subrosa.app.ui.theme.LedgerRow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun MetricsPanel(metrics: StateFlow<Metrics>) {
    val m by metrics.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProofHeader("On-device · live")
        LedgerRow("Backend", m.backendLabel)
        LedgerRow("Model load", m.modelLoadMs?.let { "$it ms" } ?: "—")
        LedgerRow("Tokens / sec", m.tokensPerSec?.let { "${it.roundToInt()}" } ?: "—")
        LedgerRow("Generation", m.genElapsedMs?.let { "$it ms" } ?: "—")
        LedgerRow("ASR latency", m.asrLatencyMs?.let { "$it ms" } ?: "—")
    }
}
