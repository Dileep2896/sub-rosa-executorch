package com.subrosa.app.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live, observable metrics sink. Inference/ASR code depends only on the narrow `report*` methods;
 * the UI observes [state]. Decoupling means a change to ExecuTorch's stats schema touches only the
 * code that calls [reportGeneration], not the panels.
 */
class MetricsCollector {

    private val _state = MutableStateFlow(Metrics())
    val state: StateFlow<Metrics> = _state.asStateFlow()

    fun setBackend(label: String) = _state.update { it.copy(backendLabel = label) }

    fun reportModelLoad(ms: Long) = _state.update { it.copy(modelLoadMs = ms) }

    fun reportAsrLatency(ms: Long) = _state.update { it.copy(asrLatencyMs = ms) }

    fun reportGeneration(tokenCount: Int, elapsedMs: Long) = _state.update {
        val tps = if (elapsedMs > 0) tokenCount * 1000f / elapsedMs else null
        it.copy(tokenCount = tokenCount, genElapsedMs = elapsedMs, tokensPerSec = tps)
    }

    /** Clear just the live generation counters (keeps backend + model-load) at the start of a run. */
    fun startGeneration() = _state.update { it.copy(tokenCount = 0, tokensPerSec = null, genElapsedMs = null) }

    fun reset() {
        _state.value = Metrics()
    }
}
