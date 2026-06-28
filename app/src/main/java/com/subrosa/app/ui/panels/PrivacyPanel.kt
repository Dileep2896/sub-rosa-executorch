package com.subrosa.app.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subrosa.app.metrics.NetworkReach
import com.subrosa.app.metrics.PrivacyStats
import com.subrosa.app.ui.theme.CoveredGreen
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.LedgerRow
import com.subrosa.app.ui.theme.MissingRed
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PrivacyPanel(privacy: StateFlow<PrivacyStats>) {
    val p by privacy.collectAsStateWithLifecycle()
    val (reachLabel, reachColor) = when (p.reach) {
        NetworkReach.BLOCKED -> "BLOCKED BY OS" to CoveredGreen
        NetworkReach.REACHABLE -> "REACHABLE (!)" to MissingRed
        NetworkReach.UNKNOWN -> "checking…" to InkSoft
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProofHeader("Privacy · sealed")
        LedgerRow(
            "Internet permission",
            if (p.internetPermissionDeclared) "DECLARED" else "NOT DECLARED",
            if (p.internetPermissionDeclared) MissingRed else CoveredGreen,
        )
        LedgerRow("Outbound socket", reachLabel, reachColor)
        LedgerRow("Bytes out · this session", p.txBytes.toString(), CoveredGreen)
        LedgerRow("Bytes in · this session", p.rxBytes.toString(), CoveredGreen)
        Spacer(Modifier.height(2.dp))
        Text(
            "No audio or transcript leaves this device.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = InkSoft,
        )
    }
}
