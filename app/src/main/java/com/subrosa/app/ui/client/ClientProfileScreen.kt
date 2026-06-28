package com.subrosa.app.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.subrosa.app.domain.model.Case
import com.subrosa.app.domain.model.Client
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.ConfirmDialog
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.InkSoft
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
fun ClientProfileScreen(
    client: Client,
    cases: List<Case>,
    matterName: (MatterType) -> String,
    onNewCase: () -> Unit,
    onOpenCase: (Case) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matterFilter by remember { mutableStateOf<MatterType?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Eyebrow("Client", color = Brass)
            Spacer(Modifier.height(6.dp))
            Text(client.name, style = MaterialTheme.typography.headlineMedium, color = Ink)
            client.note?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), color = InkSoft)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("EDIT", style = MaterialTheme.typography.labelMedium, color = Oxblood, modifier = Modifier.clickable { onEdit() })
                Text("DELETE", style = MaterialTheme.typography.labelMedium, color = MissingRed, modifier = Modifier.clickable { confirmDelete = true })
            }
        }

        val details = buildList {
            client.phone?.let { add("Phone" to it) }
            client.email?.let { add("Email" to it) }
            client.address?.let { add("Address" to it) }
        }
        if (details.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    details.forEach { (label, value) -> DetailRow(label, value) }
                }
            }
        }

        item {
            Column {
                SectionNumeral("I", "Cases")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNewCase,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Open a new case", style = MaterialTheme.typography.titleMedium) }
                if (cases.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No cases yet for this client.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = InkFaint,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    SearchField(value = query, onValueChange = { query = it }, placeholder = "Search cases")
                    val types = remember(cases) { cases.map { it.matterType }.distinct() }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterPill("All", matterFilter == null) { matterFilter = null }
                        types.forEach { t -> FilterPill(matterName(t), matterFilter == t) { matterFilter = t } }
                    }
                    val shownCases = cases.filter {
                        (matterFilter == null || it.matterType == matterFilter) &&
                            (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (shownCases.isEmpty()) {
                        Text(
                            "No cases match your search.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = InkFaint,
                        )
                    } else {
                        shownCases.forEach { CaseRow(it, matterName, onOpenCase) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete client?",
            message = "This permanently deletes ${client.name} and all of their cases, consultations, and documents.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.width(76.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun CaseRow(case: Case, matterName: (MatterType) -> String, onOpen: (Case) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(case) }.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(case.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${matterName(case.matterType)} · ${formatStamp(case.createdAtMs)}".uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
            Text("→", style = MaterialTheme.typography.titleLarge, color = Oxblood)
        }
        Spacer(Modifier.height(12.dp))
        RuleLine()
    }
}

private fun formatStamp(ms: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
