package com.subrosa.app.metrics

/**
 * Snapshot of the live on-device metrics shown in the metrics panel. The backend label is asserted
 * by whoever builds the engine (CPU vs QNN/NPU) — never inferred — so the panel stays honest.
 */
data class Metrics(
    val backendLabel: String = "—",
    val modelLoadMs: Long? = null,
    val asrLatencyMs: Long? = null,
    val tokenCount: Int = 0,
    val tokensPerSec: Float? = null,
    val genElapsedMs: Long? = null,
)
