package com.slncloth.shop.client

import com.slncloth.shop.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionStatus { CONNECTED, CONNECTING, SERVER_UNAVAILABLE }

/**
 * Periodically checks /health. On the main phone the server state is authoritative;
 * on client phones this monitor is the source of the Connected / Connecting / Server
 * unavailable indicator.
 */
class ConnectionMonitor(
    private val api: ShopApi,
    private val prefs: AppPrefs,
) {

    private val _status = MutableStateFlow(ConnectionStatus.CONNECTING)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope, pollMillis: Long = 15_000) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(pollMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refresh() {
        if (prefs.baseUrl.isBlank() || prefs.fingerprint.isBlank()) {
            _status.value = ConnectionStatus.SERVER_UNAVAILABLE
            return
        }
        _status.value = ConnectionStatus.CONNECTING
        val ok = try {
            api.health().ok
        } catch (e: Exception) {
            false
        }
        _status.value = if (ok) ConnectionStatus.CONNECTED else ConnectionStatus.SERVER_UNAVAILABLE
    }

    fun markUnavailable() {
        _status.value = ConnectionStatus.SERVER_UNAVAILABLE
    }

    fun markConnected() {
        _status.value = ConnectionStatus.CONNECTED
    }
}
