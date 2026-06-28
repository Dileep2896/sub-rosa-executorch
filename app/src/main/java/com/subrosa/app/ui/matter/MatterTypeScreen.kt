package com.subrosa.app.ui.matter

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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.MatterType
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.Reveal
import com.subrosa.app.ui.theme.RuleLine

private val ROMANS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

@Composable
fun MatterTypeScreen(checklists: List<Checklist>, onPick: (MatterType) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        Reveal(visible, 0) {
            Column {
                Eyebrow("New case", color = Brass)
                Spacer(Modifier.height(10.dp))
                Text("What kind of\nmatter?", style = MaterialTheme.typography.headlineLarge, color = Ink)
            }
        }
        Spacer(Modifier.height(30.dp))
        RuleLine()
        checklists.forEachIndexed { i, c ->
            Reveal(visible, 150 + i * 110) {
                Column(
                    Modifier.fillMaxWidth().clickable { onPick(c.matterType) }.padding(vertical = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(ROMANS.getOrElse(i) { "•" }, style = MaterialTheme.typography.titleLarge, color = Brass)
                        Column(Modifier.weight(1f)) {
                            Text(c.displayName, style = MaterialTheme.typography.headlineSmall, color = Ink)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${c.items.size} standard intake items".uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkFaint,
                            )
                        }
                        Text("→", style = MaterialTheme.typography.titleLarge, color = Oxblood)
                    }
                }
            }
            RuleLine()
        }
        Spacer(Modifier.height(40.dp))
    }
}
