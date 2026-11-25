package com.slncloth.shop.client

import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.core.api.ErrorResponse
import com.slncloth.shop.security.PinnedTls
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * HTTPS client for the shop API.
 *
 * Every request is made over TLS with the exact server certificate pinned by its SHA-256
 * fingerprint. There is no cleartext fallback and no trust-all option.
 */
class ApiClient(
    private val prefs: AppPrefs,
    private val secureStore: SecureStore,
    val json: Json = AppJson.json,
) {

    private data class Endpoint(val baseUrl: String, val fingerprint: String)

    private val sslContexts = ConcurrentHashMap<String, SSLContext>()

    fun endpointBaseUrl(): String = prefs.baseUrl
    fun endpointFingerprint(): String = prefs.fingerprint

    private fun endpoint(): Endpoint {
        val base = prefs.baseUrl.trim()
        val fingerprint = prefs.fingerprint.trim()
        if (base.isBlank() || fingerprint.isBlank()) {
            throw ApiException.client(
                "This phone is not connected to the shop server yet.",
                "NOT_PAIRED"
            )
        }
        return Endpoint(base.trimEnd('/'), fingerprint)
    }

    private fun sslContext(fingerprint: String): SSLContext =
        sslContexts.getOrPut(fingerprint) { PinnedTls.sslContext(fingerprint) }

    fun <T> encode(value: T, serializer: KSerializer<T>): String =
        json.encodeToString(serializer, value)

    suspend fun <T> get(
        path: String,
        deserializer: KSerializer<T>,
        query: Map<String, String?> = emptyMap(),
    ): T = execute("GET", path, null, deserializer, query)

    suspend fun <T> delete(
        path: String,
        deserializer: KSerializer<T>,
        query: Map<String, String?> = emptyMap(),
    ): T = execute("DELETE", path, null, deserializer, query)

    suspend fun <T> post(path: String, bodyJson: String, deserializer: KSerializer<T>): T =
        execute("POST", path, bodyJson, deserializer)

    suspend fun <T> put(path: String, bodyJson: String, deserializer: KSerializer<T>): T =
        execute("PUT", path, bodyJson, deserializer)

    suspend fun <T> patch(path: String, bodyJson: String, deserializer: KSerializer<T>): T =
        execute("PATCH", path, bodyJson, deserializer)

    private suspend fun <T> execute(
        method: String,
        path: String,
        bodyJson: String?,
        deserializer: KSerializer<T>,
        query: Map<String, String?>,
    ): T = withContext(Dispatchers.IO) {
        val endpoint = endpoint()
        val url = buildUrl(endpoint.baseUrl, path, query)

        var connection: HttpsURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = sslContext(endpoint.fingerprint).socketFactory
                hostnameVerifier = PinnedTls.hostnameVerifier(endpoint.fingerprint)
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (bodyJson != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
                }
                secureStore.get(SecureStore.KEY_DEVICE_TOKEN)?.let {
                    setRequestProperty("X-Device-Token", it)
                }
                sessionTokenProvider?.invoke()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val parsed = runCatching {
                    json.decodeFromString(ErrorResponse.serializer(), text)
                }.getOrNull()
                throw ApiException(
                    status = code,
                    code = parsed?.code ?: "HTTP_$code",
                    message = parsed?.message ?: "The shop server rejected the request."
                )
            }
            json.decodeFromString(deserializer, text)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // Timeouts, connection resets and TLS failures all mean "not confirmed".
            throw ApiException.network()
        } finally {
            connection?.disconnect()
        }
    }

    /** Set by [ShopApi] so requests carry the current session token. */
    @Volatile
    var sessionTokenProvider: (() -> String?)? = null

    private fun buildUrl(baseUrl: String, path: String, query: Map<String, String?>): String {
        val sb = StringBuilder(baseUrl)
        if (!path.startsWith("/")) sb.append('/')
        sb.append(path)
        val params = query.filterValues { it != null }
        if (params.isNotEmpty()) {
            sb.append('?')
            sb.append(params.entries.joinToString("&") { (key, value) ->
                URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            })
        }
        return sb.toString()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
