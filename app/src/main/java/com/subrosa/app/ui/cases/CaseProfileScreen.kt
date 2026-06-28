package com.subrosa.app.ui.cases

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.subrosa.app.data.docs.DocumentRef
import com.subrosa.app.data.session.SavedSession
import com.subrosa.app.domain.model.Case
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.domain.model.SessionStatus
import com.subrosa.app.ui.theme.AmbiguousAmber
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.ConfirmDialog
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Hairline
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.MissingRed
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.FilterPill
import com.subrosa.app.ui.theme.RuleLine
import com.subrosa.app.ui.theme.SearchField
import com.subrosa.app.ui.theme.SectionNumeral
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaseProfileScreen(
    case: Case,
    clientName: String,
    sessions: List<SavedSession>,
    documents: List<DocumentRef>,
    matterName: (MatterType) -> String,
    onNewConsultation: () -> Unit,
    onOpenSession: (SavedSession) -> Unit,
    onAttach: (Uri) -> Unit,
    onOpenDoc: (DocumentRef) -> Unit,
    onDeleteDoc: (DocumentRef) -> Unit,
    onDeleteCase: () -> Unit,
    onExportReport: () -> Unit,
    onDownloadReport: () -> Unit,
    onDeleteSession: (SavedSession) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttach)
    }
    var confirmDeleteCase by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SavedSession?>(null) }
    var docQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<SessionStatus?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Eyebrow("$clientName · ${matterName(case.matterType)}", color = Brass)
            Spacer(Modifier.height(6.dp))
            Text(case.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "SHARE PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = Oxblood,
                    modifier = Modifier.clickable { onExportReport() },
                )
                Text(
                    "DOWNLOAD PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = Oxblood,
                    modifier = Modifier.clickable { onDownloadReport() },
                )
                Text(
                    "DELETE CASE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MissingRed,
                    modifier = Modifier.clickable { confirmDeleteCase = true },
                )
            }
        }

        item {
            Column {
                SectionNumeral("I", "Documents")
                Spacer(Modifier.height(8.dp))
                if (documents.isEmpty()) {
                    Text(
                        "No documents on this case. Add the signed contract, an invoice, photos…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = InkFaint,
                    )
                } else {
                    if (documents.size > 2) {
                        SearchField(value = docQuery, onValueChange = { docQuery = it }, placeholder = "Search documents")
                        Spacer(Modifier.height(8.dp))
                    }
                    val shownDocs = documents.filter {
                        docQuery.isBlank() || it.displayName.contains(docQuery.trim(), ignoreCase = true)
                    }
                    if (shownDocs.isEmpty()) {
                        Text(
                            "No documents match your search.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = InkFaint,
                        )
                    } else {
                        shownDocs.forEach { DocumentRow(it, onOpenDoc, onDeleteDoc) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(4.dp)) {
                    Text("Attach a document", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Column {
                SectionNumeral("II", "Consultations")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNewConsultation,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Begin a new consultation", style = MaterialTheme.typography.titleMedium) }
                if (sessions.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No consultations yet on this case.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = InkFaint,
                    )
                } else {
                    run {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterPill("All", statusFilter == null) { statusFilter = null }
                            FilterPill("In progress", statusFilter == SessionStatus.IN_PROGRESS) { statusFilter = SessionStatus.IN_PROGRESS }
                            FilterPill("Sealed", statusFilter == SessionStatus.SEALED) { statusFilter = SessionStatus.SEALED }
                        }
                    }
                    val shownSessions = sessions.filter { statusFilter == null || it.status == statusFilter }
                    Spacer(Modifier.height(8.dp))
                    if (shownSessions.isEmpty()) {
                        Text(
                            "No consultations match this filter.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = InkFaint,
                        )
                    } else {
                        shownSessions.forEach { s -> ConsultationRow(s, onOpenSession) { sessionToDelete = s } }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (confirmDeleteCase) {
        ConfirmDialog(
            title = "Delete case?",
            message = "This permanently deletes “${case.title}” and all of its consultations and documents.",
            confirmLabel = "Delete",
            onConfirm = { confirmDeleteCase = false; onDeleteCase() },
            onDismiss = { confirmDeleteCase = false },
        )
    }
    sessionToDelete?.let { target ->
        ConfirmDialog(
            title = "Delete consultation?",
            message = "This permanently deletes this consultation's transcript and notes.",
            confirmLabel = "Delete",
            onConfirm = { sessionToDelete = null; onDeleteSession(target) },
            onDismiss = { sessionToDelete = null },
        )
    }
}

@Composable
private fun DocumentRow(doc: DocumentRef, onOpen: (DocumentRef) -> Unit, onDelete: (DocumentRef) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(doc) }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(doc.displayName, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatSize(doc.sizeBytes)} · ${formatStamp(doc.addedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
            TextButton(onClick = { onDelete(doc) }) {
                Text("REMOVE", style = MaterialTheme.typography.labelSmall, color = MissingRed)
            }
        }
        RuleLine(color = Hairline.copy(alpha = 0.5f))
    }
}

@Composable
private fun ConsultationRow(session: SavedSession, onOpen: (SavedSession) -> Unit, onDelete: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                formatStamp(session.updatedAtMs).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier.weight(1f).clickable { onOpen(session) },
            )
            StatusChip(session.status, session.notes?.missing?.size)
            Text(
                "✕",
                style = MaterialTheme.typography.labelLarge,
                color = MissingRed,
                modifier = Modifier.clickable { onDelete() }.padding(horizontal = 6.dp),
            )
        }
        RuleLine(color = Hairline.copy(alpha = 0.5f))
    }
}

@Composable
private fun StatusChip(status: SessionStatus, gaps: Int?) {
    val label: String
    val color = when (status) {
        SessionStatus.SEALED -> { label = "SEALED"; Brass }
        SessionStatus.IN_PROGRESS -> { label = if (gaps != null) "$gaps OPEN" else "IN PROGRESS"; AmbiguousAmber }
    }
    Box(Modifier.border(1.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun formatStamp(ms: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(ms))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
