package com.subrosa.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.subrosa.app.domain.model.Client
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.Reveal
import com.subrosa.app.ui.theme.RuleLine
import com.subrosa.app.ui.theme.SealEmblem
import com.subrosa.app.ui.theme.SearchField

@Composable
fun HomeScreen(
    clients: List<Client>,
    onNewClient: () -> Unit,
    onOpenClient: (Client) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    var query by remember { mutableStateOf("") }
    val shown = remember(clients, query) { clients.filter { clientMatches(it, query) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp)) {
        Spacer(Modifier.height(28.dp))
        Reveal(visible, 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SealEmblem(diameter = 52.dp)
                Spacer(Modifier.height(14.dp))
                Eyebrow("Sub Rosa · Confidential", color = Brass)
                Spacer(Modifier.height(8.dp))
                Text("Clients", style = MaterialTheme.typography.displaySmall, color = Ink)
            }
        }
        Spacer(Modifier.height(22.dp))
        Reveal(visible, 100) {
            Button(
                onClick = onNewClient,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("New client", style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(24.dp))
        Reveal(visible, 180) {
            Column {
                Eyebrow("The record room · ${clients.size} client${if (clients.size == 1) "" else "s"}", color = InkFaint)
                Spacer(Modifier.height(10.dp))
                RuleLine()
            }
        }
        if (clients.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search clients")
        }
        when {
            clients.isEmpty() -> {
                Spacer(Modifier.height(20.dp))
                Text(
                    "No clients yet. Create one to begin — everything stays sealed on this device.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = InkFaint,
                )
            }
            shown.isEmpty() -> {
                Spacer(Modifier.height(20.dp))
                Text(
                    "No clients match “$query”.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = InkFaint,
                )
            }
            else -> {
                Spacer(Modifier.height(6.dp))
                shown.forEachIndexed { i, c -> Reveal(visible, 220 + minOf(i, 6) * 45) { ClientRow(c, onOpenClient) } }
            }
        }
        Spacer(Modifier.height(44.dp))
    }
}

private fun clientMatches(c: Client, q: String): Boolean {
    if (q.isBlank()) return true
    val hay = listOfNotNull(c.name, c.phone, c.email, c.address, c.note).joinToString(" ").lowercase()
    return hay.contains(q.trim().lowercase())
}

@Composable
private fun ClientRow(client: Client, onOpen: (Client) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(client) }.padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(client.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    (client.note ?: client.phone ?: "Tap to open").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                )
            }
            Text("→", style = MaterialTheme.typography.titleLarge, color = Oxblood)
        }
        Spacer(Modifier.height(14.dp))
        RuleLine()
    }
}
