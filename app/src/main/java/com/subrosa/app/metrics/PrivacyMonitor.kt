package com.subrosa.app.metrics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket

/** Result of actively trying to open an outbound socket. */
enum class NetworkReach { UNKNOWN, BLOCKED, REACHABLE }

data class PrivacyStats(
    val internetPermissionDeclared: Boolean,
    /** Bytes sent/received attributed to this app *during this session* (delta from launch). */
    val txBytes: Long,
    val rxBytes: Long,
    /** Whether the OS lets this app reach the network at all. */
    val reach: NetworkReach,
)

/**
 * Backs the privacy panel with REAL OS signals (never hardcoded):
 *  - whether this app even declares the INTERNET permission (it must NOT — "private by construction");
 *  - bytes the OS attributes to this app *since launch* (a delta, so pre-existing per-UID history —
 *    e.g. a recycled UID on the emulator — doesn't masquerade as this app's traffic);
 *  - a live probe that tries to open an outbound socket and shows the OS denying it.
 */
class PrivacyMonitor(app: Application) {

    private val uid = Process.myUid()

    // Baseline at construction. TrafficStats per-UID counters are cumulative since boot and can carry
    // history from a previously-installed app that reused this UID — we only want OUR delta.
    private val baseTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
    private val baseRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)

    val internetPermissionDeclared: Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
    }.getOrDefault(false)

    private val _state = MutableStateFlow(snapshot(NetworkReach.UNKNOWN))
    val state: StateFlow<PrivacyStats> = _state.asStateFlow()

    fun refresh() {
        _state.value = snapshot(_state.value.reach)
    }

    /**
     * Actively prove the security property: try to connect outbound. With no INTERNET permission the
     * OS denies the socket (EACCES) → [NetworkReach.BLOCKED]. A successful connect would be a privacy
     * failure → [NetworkReach.REACHABLE]. Runs off the main thread.
     */
    suspend fun probe() {
        val reach = withContext(Dispatchers.IO) { attemptOutbound() }
        _state.value = snapshot(reach)
    }

    private fun attemptOutbound(): NetworkReach = try {
        // Literal IP (no DNS, which is also blocked) — a name server, port 53.
        Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1500) }
        NetworkReach.REACHABLE // got out — should be impossible without INTERNET permission
    } catch (t: Throwable) {
        // EACCES / SecurityException / any failure → the app could not reach the network.
        NetworkReach.BLOCKED
    }

    private fun snapshot(reach: NetworkReach) = PrivacyStats(
        internetPermissionDeclared = internetPermissionDeclared,
        txBytes = (TrafficStats.getUidTxBytes(uid).coerceAtLeast(0) - baseTx).coerceAtLeast(0),
        rxBytes = (TrafficStats.getUidRxBytes(uid).coerceAtLeast(0) - baseRx).coerceAtLeast(0),
        reach = reach,
    )
}
