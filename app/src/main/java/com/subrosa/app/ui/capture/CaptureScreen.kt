package com.subrosa.app.ui.capture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.Speaker
import com.subrosa.app.domain.model.TranscriptSegment
import com.subrosa.app.ui.SessionUiState
import com.subrosa.app.ui.theme.AmbiguousAmber
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Hairline
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.PaperHigh
import com.subrosa.app.ui.theme.RuleLine

@Composable
fun CaptureScreen(
    ui: SessionUiState,
    warmupReady: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLoadScript: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(18.dp))
        RecordingHeader(recording = ui.isRecording)
        Spacer(Modifier.height(14.dp))
        RuleLine()

        // Continuing a consultation: surface what the first pass already learned + what's still to probe.
        ui.notesResult?.notesOrNull?.let { prior ->
            Spacer(Modifier.height(12.dp))
            PriorPassPanel(prior)
        }

        val listState = rememberLazyListState()
        // Auto-scroll only when a new committed line lands — the live caption updates in its own strip,
        // so the committed transcript above never jitters as whisper revises the in-progress words.
        LaunchedEffect(ui.segments.size) {
            if (ui.segments.isNotEmpty()) listState.animateScrollToItem(ui.segments.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (ui.segments.isEmpty()) {
                item {
                    Text(
                        if (ui.isRecording) "Listening… the transcript appears here, line by line."
                        else "The transcript will appear here, line by line, as the consultation is recorded.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = InkFaint,
                    )
                }
            }
            // Each new line fades in (animateItem) and carries a slim left accent for who's speaking.
            itemsIndexed(ui.segments) { _, seg ->
                MemoLine(seg, Modifier.animateItem(fadeInSpec = tween(460)))
            }
        }

        // Live caption — the in-progress words in their own calm strip; the committed lines above are the
        // "best" transcript and are what the on-device model receives.
        if (ui.isRecording) {
            RuleLine(color = Hairline.copy(alpha = 0.5f))
            LiveCaptionStrip(ui.partialText)
        }
        Spacer(Modifier.height(8.dp))

        val modelsReady = warmupReady
        Button(
            onClick = { if (ui.isRecording) onStop() else onStart() },
            enabled = ui.isRecording || modelsReady,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Oxblood,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = PaperHigh,
                disabledContentColor = InkFaint,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                when {
                    ui.isRecording -> "Stop & seal the record"
                    !modelsReady -> "Warming up the on-device AI…"
                    ui.notesResult?.notesOrNull != null -> "Resume recording"
                    else -> "Start recording"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        // Demo: stream a scripted consultation (opening, or the follow-up keyed to the report's gaps) into
        // the live transcript; Stop & seal then runs the real on-device pipeline over it.
        if (onLoadScript != null && !ui.isRecording) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLoadScript, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (ui.notesResult?.notesOrNull != null) "▶  Load follow-up answers (demo)"
                    else "▶  Load scripted consultation (demo)",
                    style = MaterialTheme.typography.labelLarge,
                    color = Oxblood,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Shown when continuing a consultation: what the first pass already learned, what's still missing, and the
 * follow-ups to ask — so the lawyer knows what to probe next instead of flying blind. Collapsible.
 */
@Composable
private fun PriorPassPanel(notes: Notes) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PaperHigh)
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("From the first pass", color = Brass)
            Spacer(Modifier.weight(1f))
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium, color = Oxblood)
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${notes.facts.size} fact${if (notes.facts.size == 1) "" else "s"} captured so far.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
                if (notes.missing.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("Still to cover", color = Oxblood)
                    Spacer(Modifier.height(6.dp))
                    notes.missing.take(8).forEach { item ->
                        Text("•  $item", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Spacer(Modifier.height(3.dp))
                    }
                }
                if (notes.prompts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Eyebrow("Questions to ask", color = Brass)
                    Spacer(Modifier.height(6.dp))
                    notes.prompts.take(6).forEach { q ->
                        Text(
                            "→  $q",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = InkSoft,
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

/** Calm recording status — no speaker controls; who's speaking is inferred from the conversation. */
@Composable
private fun RecordingHeader(recording: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(if (recording) "Recording" else "Ready", color = Brass)
            Spacer(Modifier.height(4.dp))
            Text(
                if (recording) "Capturing the consultation on-device. Lawyer and client are sorted out automatically."
                else "Press start when you're ready — everything stays on the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
        if (recording) RecPulse()
    }
}

@Composable
private fun RecPulse() {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("REC", style = MaterialTheme.typography.labelSmall, color = Oxblood)
        Box(Modifier.size(11.dp).clip(CircleShape).background(Oxblood.copy(alpha = alpha)))
    }
}

/** The live, in-progress words — a calm fixed caption strip so the committed transcript above stays stable. */
@Composable
private fun LiveCaptionStrip(partial: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListeningBars()
        Text(
            partial.ifBlank { "Listening…" },
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = if (partial.isBlank()) InkFaint else InkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A small animated equalizer that signals active listening. */
@Composable
private fun ListeningBars() {
    val t = rememberInfiniteTransition(label = "listen")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            val h by t.animateFloat(
                initialValue = 5f,
                targetValue = 17f,
                animationSpec = infiniteRepeatable(tween(380 + i * 110), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(Oxblood))
        }
    }
}

/**
 * One committed transcript line. The speaker shows only as a slim left accent rule — brass for the lawyer,
 * oxblood for the client — inferred from content, never a clunky LW/CL tag. The line fades in via the
 * list's animateItem.
 */
@Composable
private fun MemoLine(seg: TranscriptSegment, modifier: Modifier = Modifier) {
    val isClient = seg.speaker == Speaker.CLIENT
    val accent = if (isClient) Oxblood else Brass
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 3.dp, bottom = 3.dp)
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(accent.copy(alpha = if (isClient) 0.9f else 0.5f)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                seg.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isClient) Ink else InkSoft,
            )
            if (seg.isLowConfidence) {
                Spacer(Modifier.height(3.dp))
                Text("◦ low confidence", style = MaterialTheme.typography.labelSmall, color = AmbiguousAmber)
            }
        }
    }
}
