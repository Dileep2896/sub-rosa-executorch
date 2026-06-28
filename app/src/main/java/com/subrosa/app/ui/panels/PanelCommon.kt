package com.subrosa.app.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.RuleLine

/** Shared header for a proof ledger: brass eyebrow + hairline rule. */
@Composable
internal fun ProofHeader(title: String) {
    Column {
        Eyebrow(title, color = Brass)
        Spacer(Modifier.height(8.dp))
        RuleLine()
        Spacer(Modifier.height(12.dp))
    }
}
