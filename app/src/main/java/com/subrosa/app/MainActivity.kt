package com.subrosa.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subrosa.app.ui.BiometricGate
import com.subrosa.app.ui.LockScreen
import com.subrosa.app.ui.SessionViewModel
import com.subrosa.app.ui.SubRosaApp

/**
 * Single Activity host. A [FragmentActivity] so it can host the biometric prompt. On launch it gates
 * behind a biometric / device-credential unlock when the device supports it; otherwise it opens
 * straight to the app.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SubRosaApplication).container
        setContent {
            var unlocked by remember { mutableStateOf(!BiometricGate.isAvailable(this@MainActivity)) }
            if (unlocked) {
                val vm: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
                SubRosaApp(vm)
            } else {
                LockScreen(onUnlock = { BiometricGate.authenticate(this@MainActivity) { unlocked = true } })
                LaunchedEffect(Unit) { BiometricGate.authenticate(this@MainActivity) { unlocked = true } }
            }
        }
    }
}
