package com.slncloth.shop.server

import android.util.Log
import com.slncloth.shop.client.HealthProbe
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.server.tls.ServerTls
import com.slncloth.shop.util.AppPrefs
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.sslConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Starts and stops the embedded HTTPS server and publishes its state to the UI.
 *
 * Binding rules:
 *  - binds to the advertised hotspot address only (never 0.0.0.0);
 *  - falls back to 127.0.0.1 when no usable interface exists, so the main phone can still
 *    bill locally. Clients then need the hotspot turned on and the server restarted;
 *  - if the configured port is taken, tries the next ten ports and remembers the one used.
 */
class ServerController(
    private val prefs: AppPrefs,
    private val tls: ServerTls,
    private val services: ServerServices,
) {

    sealed interface State {
        data object Idle : State
        data object Starting : State
        data class Running(
            val address: String,
            val port: Int,
            val baseUrl: String,
            val fingerprint: String,
            val verified: Boolean,
            val isLoopback: Boolean,
        ) : State

        data class Failed(val message: String) : State
        data object Stopping : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var engine: ApplicationEngine? = null
    private val mutex = Mutex()

    val isRunning: Boolean
        get() = _state.value is State.Running

    suspend fun start(): State = mutex.withLock {
        if (_state.value is State.Running) return@withLock _state.value

        _state.value = State.Starting
        try {
            val identity = withContext(Dispatchers.IO) { tls.ensureIdentity() }
            val (address, port) = bindAddressAndPort()

            val environment = applicationEngineEnvironment {
                module {
                    shopModule(services) { identity.fingerprint }
                }
                sslConnector(
                    keyStore = identity.keyStore(),
                    keyAlias = identity.alias,
                    keyStorePassword = { identity.password },
                    privateKeyPassword = { identity.password },
                ) {
                    host = address
                    this.port = port
                    connectionIdleTimeoutSeconds = 120
                }
            }

            val newEngine = CIO.createApplicationEngine(environment)
            withContext(Dispatchers.IO) { newEngine.start(wait = false) }
            engine = newEngine

            val baseUrl = "https://$address:$port"
            prefs.baseUrl = baseUrl
            prefs.fingerprint = identity.fingerprint
            prefs.serverPort = port

            val verified = HealthProbe.check(baseUrl, identity.fingerprint)
            Log.i(TAG, "Server started on $baseUrl (verified=$verified)")

            val running = State.Running(
                address = address,
                port = port,
                baseUrl = baseUrl,
                fingerprint = identity.fingerprint,
                verified = verified,
                isLoopback = address == "127.0.0.1",
            )
            _state.value = running
            running
        } catch (e: Exception) {
            Log.e(TAG, "Server failed to start: ${e::class.java.simpleName}")
            stopEngine()
            _state.value = State.Failed(
                when (e) {
                    is java.net.BindException -> "Could not bind the server port. Try another port in Settings."
                    else -> "The shop server could not start. Check the hotspot and try again."
                }
            )
            _state.value
        }
    }

    /** Stops the embedded server and clears the advertised address. */
    fun stop() {
        runBlocking {
            mutex.withLock {
                _state.value = State.Stopping
                stopEngine()
                prefs.baseUrl = ""
                _state.value = State.Idle
            }
        }
    }

    suspend fun stopAsync() {
        mutex.withLock {
            _state.value = State.Stopping
            stopEngine()
            prefs.baseUrl = ""
            _state.value = State.Idle
        }
    }

    private fun stopEngine() {
        try {
            engine?.stop(500, 1_000)
        } catch (e: Exception) {
            Log.w(TAG, "Error while stopping server: ${e::class.java.simpleName}")
        }
        engine = null
    }

    private fun bindAddressAndPort(): Pair<String, Int> {
        val configuredAddress = prefs.bindAddress.trim()
        val address = when {
            configuredAddress.isNotEmpty() && LocalAddresses.isValidIpv4(configuredAddress) -> configuredAddress
            else -> LocalAddresses.preferred()?.address ?: "127.0.0.1"
        }

        val preferredPort = prefs.serverPort
        val candidates = (preferredPort..preferredPort + 9).toList()
        for (port in candidates) {
            if (canBind(address, port)) return address to port
        }
        throw java.net.BindException("No free port in range $preferredPort..${preferredPort + 9}")
    }

    private fun canBind(address: String, port: Int): Boolean {
        return try {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress(address, port), 1)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Only used by the UI to describe a state in plain language. */
    fun describe(state: State): String = when (state) {
        is State.Idle -> "Server stopped"
        is State.Starting -> "Starting server…"
        is State.Stopping -> "Stopping server…"
        is State.Failed -> state.message
        is State.Running -> if (state.verified) "Running on ${state.address}:${state.port}" else "Running, but not reachable on ${state.address}:${state.port}"
    }

    companion object {
        private const val TAG = "ServerController"
    }
}
