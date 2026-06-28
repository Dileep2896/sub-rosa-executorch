package com.subrosa.app.ui.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subrosa.app.domain.model.Client
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkFaint
import com.subrosa.app.ui.theme.Oxblood

@Composable
fun NewClientScreen(
    initial: Client?,
    onSubmit: (name: String, phone: String, email: String, address: String, note: String) -> Unit,
    onCancel: () -> Unit,
) {
    val editing = initial != null
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone ?: "") }
    var email by remember(initial?.id) { mutableStateOf(initial?.email ?: "") }
    var address by remember(initial?.id) { mutableStateOf(initial?.address ?: "") }
    var note by remember(initial?.id) { mutableStateOf(initial?.note ?: "") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Eyebrow(if (editing) "Edit client" else "New client", color = Brass)
        Spacer(Modifier.height(8.dp))
        Text("Client details", style = MaterialTheme.typography.displaySmall, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (editing) "Update and save." else "Stored locally and encrypted. Only a name is required.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint,
        )
        Spacer(Modifier.height(24.dp))

        Field("Full name", name, { name = it })
        Field("Phone", phone, { phone = it }, KeyboardType.Phone)
        Field("Email", email, { email = it }, KeyboardType.Email)
        Field("Address", address, { address = it })
        Field("Note (e.g. referral, matter summary)", note, { note = it }, singleLine = false)

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(name, phone, email, address, note) },
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(if (editing) "Save changes" else "Create client", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", style = MaterialTheme.typography.labelMedium, color = InkFaint)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
}
