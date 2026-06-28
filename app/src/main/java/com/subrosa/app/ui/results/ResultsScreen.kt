package com.subrosa.app.ui.results

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.Fact
import com.subrosa.app.domain.model.Notes
import com.subrosa.app.domain.model.NotesResult
import com.subrosa.app.ui.SessionUiState
import com.subrosa.app.ui.theme.AmbiguousAmber
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.CoveredGreen
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Hairline
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.MissingRed
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.PaperHigh
import com.subrosa.app.ui.theme.Reveal
import com.subrosa.app.ui.theme.RuleLine
import com.subrosa.app.ui.theme.SectionNumeral

@Composable
fun ResultsScreen(
    ui: SessionUiState,
    checklist: Checklist?,
    canResume: Boolean,
    onContinue: () -> Unit,
    onSeal: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val result = ui.notesResult
    val notes = result?.notesOrNull

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(Modifier.fillMaxSize()) {
        // Scrolling record.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Column {
                    Eyebrow(
                        (if (canResume) "Working record" else "Sealed record") + " · ${checklist?.displayName ?: ""}",
                        color = Brass,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("The Record", style = MaterialTheme.typography.displaySmall, color = Ink)
                }
            }

            when (result) {
                is NotesResult.Partial -> item { DegradedBanner("Some output was incomplete and was repaired.", result.errors) }
                is NotesResult.Failed -> item { DegradedBanner("The model output could not be parsed.", listOf(result.reason)) }
                is NotesResult.Insufficient -> item { Reveal(visible, 80) { InsufficientNotice(result.reason) } }
                else -> Unit
            }

            if (notes != null && checklist != null) {
                item { Reveal(visible, 80) { MissingScorecard(checklist, notes, visible) } }
                item {
                    Reveal(visible, 220) {
                        Column {
                            SectionNumeral("I", "Facts on the record")
                            Spacer(Modifier.height(8.dp))
                            notes.facts.forEachIndexed { i, f -> FactEntry(i + 1, f) }
                        }
                    }
                }
                if (notes.prompts.isNotEmpty()) {
                    item {
                        Reveal(visible, 320) {
                            Column {
                                SectionNumeral("II", "Suggested follow-ups")
                                Spacer(Modifier.height(12.dp))
                                notes.prompts.forEach { PromptRow(it) }
                            }
                        }
                    }
                }
                item {
                    Eyebrow("Sealed locally · nothing transmitted", color = InkFaint)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Persistent action bar — always visible, never scrolled away.
        ResultsActionBar(
            canResume = canResume,
            hasNotes = notes != null,
            insufficient = result is NotesResult.Insufficient,
            onContinue = onContinue,
            onSeal = onSeal,
            onHome = onHome,
            onShare = { notes?.let { shareNotes(context, it) } },
        )
    }
}

@Composable
private fun ResultsActionBar(
    canResume: Boolean,
    hasNotes: Boolean,
    insufficient: Boolean,
    onContinue: () -> Unit,
    onSeal: () -> Unit,
    onHome: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(color = PaperHigh, shadowElevation = 10.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            when {
                insufficient -> {
                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Continue recording", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onHome, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Back to case", style = MaterialTheme.typography.labelLarge)
                    }
                }
                canResume && hasNotes -> {
                    Text(
                        "Ask the follow-ups, then continue — the record grows and the gaps update.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = InkSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Continue consultation", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSeal, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f)) {
                            Text("Seal & finish", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = onShare, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f)) {
                            Text("Share", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onHome,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.weight(1f),
                        ) { Text("Back to sessions", style = MaterialTheme.typography.labelLarge) }
                        if (hasNotes) {
                            OutlinedButton(onClick = onShare, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f)) {
                                Text("Share", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingScorecard(checklist: Checklist, notes: Notes, animate: Boolean) {
    val missingSet = notes.missing.toSet()
    val missingCount = checklist.items.count { it.label in missingSet }
    val shown by animateIntAsState(if (animate) missingCount else 0, tween(950), label = "count")
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$shown", style = MaterialTheme.typography.displayLarge, color = if (missingCount > 0) Oxblood else CoveredGreen)
            Text(
                " / ${checklist.items.size}",
                style = MaterialTheme.typography.headlineMedium,
                color = InkFaint,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        Eyebrow(if (missingCount > 0) "Intake items not covered" else "All intake items covered", color = InkSoft)
        Spacer(Modifier.height(18.dp))
        checklist.items.forEach { item ->
            val missing = item.label in missingSet
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    if (missing) "✕" else "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (missing) MissingRed else CoveredGreen,
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (missing) MissingRed else Ink,
                    fontWeight = if (missing) FontWeight.Bold else FontWeight.Normal,
                )
            }
            RuleLine(color = Hairline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun FactEntry(index: Int, fact: Fact) {
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(index.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium, color = Brass)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fact.statement,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (fact.verified) Ink else InkFaint,
                    modifier = Modifier.weight(1f),
                )
                if (!fact.verified) {
                    Text("UNVERIFIED", style = MaterialTheme.typography.labelSmall, color = MissingRed)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "“${fact.sourceQuote}”",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun PromptRow(prompt: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("❧", style = MaterialTheme.typography.titleMedium, color = Brass)
        Text(prompt, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
    }
}

/** Shown when the consultation was too thin (lawyer-only / too few words) to summarize — no notes. */
@Composable
private fun InsufficientNotice(reason: String) {
    Surface(
        color = PaperHigh,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AmbiguousAmber),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Eyebrow("Not enough to summarize", color = AmbiguousAmber)
            Spacer(Modifier.height(10.dp))
            Text(reason, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "No notes were generated — Sub Rosa won't summarize a consultation it can't stand behind.",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun DegradedBanner(message: String, details: List<String>) {
    Column {
        Eyebrow("Notice", color = MissingRed)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Ink)
        details.forEach {
            Text("— $it", style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
    }
}

private fun shareNotes(context: Context, notes: Notes) {
    val text = buildString {
        appendLine("INTAKE NOTES (Sub Rosa)")
        appendLine()
        appendLine("FACTS")
        notes.facts.forEach { appendLine("- ${it.statement}  [\"${it.sourceQuote}\"]") }
        appendLine()
        appendLine("MISSING INFORMATION")
        if (notes.missing.isEmpty()) appendLine("- (none outstanding)")
        notes.missing.forEach { appendLine("- $it") }
        appendLine()
        appendLine("SUGGESTED FOLLOW-UPS")
        notes.prompts.forEach { appendLine("- $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Intake notes")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share notes"))
}
