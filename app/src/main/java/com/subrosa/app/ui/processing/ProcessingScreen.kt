package com.subrosa.app.ui.processing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subrosa.app.metrics.Metrics
import com.subrosa.app.ui.ProcessingStage
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Hairline
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.LedgerRow
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.SealEmblem
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(
    stage: StateFlow<ProcessingStage>,
    metrics: StateFlow<Metrics>,
    onCancel: () -> Unit,
) {
    val m by metrics.collectAsStateWithLifecycle()
    val st by stage.collectAsStateWithLifecycle()
    val spin = rememberInfiniteTransition(label = "seal")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rot",
    )
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SealEmblem(modifier = Modifier.graphicsLayer { rotationZ = rotation }, diameter = 84.dp)
        Spacer(Modifier.height(26.dp))
        Eyebrow("Working on-device", color = Brass)
        Spacer(Modifier.height(10.dp))
        Text("Sealing the record", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Spacer(Modifier.height(28.dp))

        // The real pipeline stages, lighting up as each one completes.
        Column(
            Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StageRow("Transcript captured", statusOf(st, ProcessingStage.PREPARING))
            StageRow("Identifying speakers", statusOf(st, ProcessingStage.ATTRIBUTING))
            StageRow("Extracting intake notes", statusOf(st, ProcessingStage.REASONING))
        }
        Spacer(Modifier.height(26.dp))

        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
            LedgerRow("Backend", m.backendLabel)
            Spacer(Modifier.height(10.dp))
            LedgerRow("Tokens / sec", m.tokensPerSec?.let { "${it.roundToInt()}" } ?: "—")
        }
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onCancel) {
            Text("CANCEL", style = MaterialTheme.typography.labelMedium, color = InkSoft)
        }
    }
}

private enum class StageStatus { PENDING, ACTIVE, DONE }

private fun statusOf(current: ProcessingStage, row: ProcessingStage): StageStatus = when {
    current.ordinal > row.ordinal -> StageStatus.DONE
    current.ordinal == row.ordinal -> StageStatus.ACTIVE
    else -> StageStatus.PENDING
}

@Composable
private fun StageRow(label: String, status: StageStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StageMarker(status)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == StageStatus.PENDING) InkFaint else Ink,
        )
    }
}

@Composable
private fun StageMarker(status: StageStatus) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (status) {
            StageStatus.DONE -> Text("✓", style = MaterialTheme.typography.labelLarge, color = Brass)
            StageStatus.PENDING -> Box(Modifier.size(10.dp).clip(CircleShape).background(Hairline))
            StageStatus.ACTIVE -> {
                val t = rememberInfiniteTransition(label = "active")
                val a by t.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "a",
                )
                Box(Modifier.size(11.dp).clip(CircleShape).background(Oxblood.copy(alpha = a)))
            }
        }
    }
}
