package com.slncloth.shop.client

import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.core.api.SessionResponse
import com.slncloth.shop.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredSession(
    val token: String,
    val role: String,
    val userName: String,
    val expiresAt: Long,
    val deviceId: String,
    val deviceName: String,
) {
    val isOwner: Boolean get() = role == "OWNER"
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
}

/**
 * Session token is held in memory and persisted encrypted with the Keystore-backed store.
 * Only the token's SHA-256 hash exists on the server.
 */
class SessionStore(
    private val secureStore: SecureStore,
    private val json: Json = AppJson.json,
) {

    private val _session = MutableStateFlow<StoredSession?>(null)
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    fun load() {
        val raw = secureStore.get(KEY)
        val parsed = raw?.let {
            runCatching { json.decodeFromString(StoredSession.serializer(), it) }.getOrNull()
        }
        _session.value = parsed?.takeUnless { it.isExpired }
    }

    fun save(session: StoredSession) {
        secureStore.put(KEY, json.encodeToString(StoredSession.serializer(), session))
        _session.value = session
    }

    fun save(response: SessionResponse, token: String) {
        save(
            StoredSession(
                token = token,
                role = response.role,
                userName = response.userName,
                expiresAt = response.expiresAt,
                deviceId = response.deviceId,
                deviceName = response.deviceName,
            )
        )
    }

    fun updateFrom(response: SessionResponse) {
        val current = _session.value ?: return
        save(current.copy(role = response.role, userName = response.userName, expiresAt = response.expiresAt))
    }

    fun clear() {
        secureStore.remove(KEY)
        _session.value = null
    }

    fun token(): String? = _session.value?.token

    companion object {
        const val KEY = "session_json"
    }
}
