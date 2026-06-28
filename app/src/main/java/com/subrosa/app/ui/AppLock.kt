package com.subrosa.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.SealEmblem
import com.subrosa.app.ui.theme.SubRosaTheme
import com.subrosa.app.ui.theme.rememberDossierPaper

/** Biometric / device-credential gate. If the device has neither, the app simply opens unlocked. */
object BiometricGate {
    private val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Sub Rosa")
            .setSubtitle("Privileged consultation records")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    SubRosaTheme {
        Box(Modifier.fillMaxSize().then(rememberDossierPaper()), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 36.dp),
            ) {
                SealEmblem(diameter = 100.dp)
                Spacer(Modifier.height(24.dp))
                Eyebrow("Confidential · Locked", color = Brass)
                Spacer(Modifier.height(10.dp))
                Text("Sub Rosa", style = MaterialTheme.typography.displaySmall, color = Oxblood)
                Spacer(Modifier.height(8.dp))
                Text(
                    "These records are sealed on this device. Unlock to continue.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onUnlock,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Oxblood, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) { Text("Unlock", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
