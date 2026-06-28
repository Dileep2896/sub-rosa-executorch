package com.subrosa.app.ui.consent

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.subrosa.app.ui.theme.Brass
import com.subrosa.app.ui.theme.Eyebrow
import com.subrosa.app.ui.theme.Fraunces
import com.subrosa.app.ui.theme.Ink
import com.subrosa.app.ui.theme.InkSoft
import com.subrosa.app.ui.theme.Oxblood
import com.subrosa.app.ui.theme.Reveal
import com.subrosa.app.ui.theme.RuleLine
import com.subrosa.app.ui.theme.SealEmblem

private const val MIC_PERMISSION_REQUEST = 4242

@Composable
fun ConsentScreen(
    onConsent: () -> Unit,
) {
    val context = LocalContext.current
    val micGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val sealScale by animateFloatAsState(if (visible) 1f else 0.82f, animationSpec = tween(700), label = "seal")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Reveal(visible, 0) { SealEmblem(modifier = Modifier.scale(sealScale), diameter = 56.dp) }
        Spacer(Modifier.height(14.dp))
        Reveal(visible, 120) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow("Confidential · Privileged · On-device", color = Brass)
                Spacer(Modifier.height(14.dp))
                Text("Sub Rosa", style = MaterialTheme.typography.displaySmall, color = Oxblood)
                Spacer(Modifier.height(4.dp))
                Text(
                    "On-device consultation notes",
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = InkSoft,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Reveal(visible, 260) {
            Column {
                RuleLine()
                Spacer(Modifier.height(18.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Black, fontSize = 46.sp, color = Oxblood)) {
                            append("T")
                        }
                        append("his tool records and transcribes locally on this device. No audio or transcript is sent to the cloud — the app holds no network permission with which to do so.")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                )
                Spacer(Modifier.height(18.dp))
                RuleLine()
            }
        }
        Spacer(Modifier.height(30.dp))
        Reveal(visible, 400) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Do you consent to recording for note-taking?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        // Request via ActivityCompat with a 16-bit-safe code — the Compose
                        // ActivityResult launcher crashes on a FragmentActivity host (used for biometric).
                        if (!micGranted) {
                            (context as? Activity)?.let {
                                ActivityCompat.requestPermissions(
                                    it,
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    MIC_PERMISSION_REQUEST,
                                )
                            }
                        }
                        onConsent()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Oxblood,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("I consent", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { }) {
                    Text("DECLINE", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                }
            }
        }
        Spacer(Modifier.height(56.dp))
    }
}
