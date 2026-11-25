package com.slncloth.shop.client

import android.os.Build
import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.core.api.PairRequest
import com.slncloth.shop.security.PinnedTls
import com.slncloth.shop.security.SecureStore
import com.slncloth.shop.server.LocalAddresses
import com.slncloth.shop.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

@Serializable
data class PairPayload(
    val v: Int = 1,
    val url: String,
    /** Full SHA-256 fingerprint of the server certificate (hex). */
    val fp: String,
    /** One-time pairing token. */
    val t: String,
    /** Shop name for display only — not a secret. */
    val s: String,
)

/** Compact QR payload: "SLNP1:" + base64url(JSON). */
object PairQr {

    const val PREFIX = "SLNP1:"

    fun encode(url: String, fingerprint: String, token: String, shopName: String): String {
        val payload = PairPayload(url = url, fp = fingerprint, t = token, s = shopName)
        val json = AppJson.json.encodeToString(PairPayload.serializer(), payload)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): PairPayload? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
            AppJson.json.decodeFromString(PairPayload.serializer(), String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }
}

/**
 * Pairing a client phone with the main phone.
 *
 * QR flow (recommended): the QR carries the full 64-character certificate fingerprint, so
 * the client pins the exact certificate before it sends the pairing token.
 *
 * Manual flow: the user types address, port, pairing code and the first 8 characters of the
 * fingerprint shown on the main phone. The client first performs a certificate-only request
 * to /health (no secrets), checks that the captured fingerprint starts with the typed code,
 * then pins it and sends the pairing token.
 */
class PairingManager(
    private val prefs: AppPrefs,
    private val secureStore: SecureStore,
    private val api: ShopApi,
) {

    data class ManualRequest(
        val host: String,
        val port: Int,
        val pairingCode: String,
        val fingerprintPrefix: String,
    )

    suspend fun pairWithQr(raw: String): String {
        val payload = PairQr.decode(raw)
            ?: throw ApiException.client("That QR code is not a shop pairing code", "INVALID_QR")
        if (!payload.url.startsWith("https://")) {
            throw ApiException.client("Pairing address must use HTTPS", "INVALID_QR")
        }
        prefs.baseUrl = payload.url.trimEnd('/')
        prefs.fingerprint = payload.fp.lowercase()
        return completePairing(payload.t)
    }

    suspend fun pairManually(request: ManualRequest): String {
        val host = request.host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        if (!LocalAddresses.isValidIpv4(host)) {
            throw ApiException.client("Enter the main phone's IP address, for example 192.168.43.1", "INVALID_HOST")
        }
        if (request.port !in 1024..65_535) {
            throw ApiException.client("Port must be between 1024 and 65535", "INVALID_PORT")
        }
        if (request.pairingCode.length !in 4..80) {
            throw ApiException.client("Enter the pairing code shown on the main phone", "INVALID_CODE")
        }
        val prefix = request.fingerprintPrefix.trim().lowercase().replace("-", "")
        if (prefix.length < 8 || prefix.any { it !in "0123456789abcdef" }) {
            throw ApiException.client("Enter the first 8 characters of the security code", "INVALID_FINGERPRINT")
        }

        val baseUrl = "https://$host:${request.port}"
        val captured = captureFingerprint(baseUrl)
        if (!captured.startsWith(prefix)) {
            throw ApiException.client(
                "Security code did not match this server. Check the code shown on the main phone.",
                "FINGERPRINT_MISMATCH"
            )
        }
        prefs.baseUrl = baseUrl
        prefs.fingerprint = captured
        return completePairing(request.pairingCode)
    }

    private suspend fun completePairing(token: String): String {
        val deviceName = prefs.deviceName.ifBlank { Build.MODEL ?: "Android phone" }
        val response = api.pair(
            PairRequest(
                pairingToken = token,
                deviceName = deviceName,
                clientVersion = "1.0.0",
            )
        )
        secureStore.put(SecureStore.KEY_DEVICE_TOKEN, response.deviceToken)
        prefs.deviceName = deviceName
        prefs.mode = com.slncloth.shop.util.AppMode.CLIENT
        prefs.configured = true
        prefs.onboardingDone = true
        return response.shopName
    }

    /** TOFU capture: fetches only the public /health endpoint and records the leaf certificate. */
    private suspend fun captureFingerprint(baseUrl: String): String = withContext(Dispatchers.IO) {
        val (context, trustManager) = PinnedTls.captureContext()
        var connection: HttpsURLConnection? = null
        try {
            connection = (URL("$baseUrl/api/v1/health").openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = context.socketFactory
                // Capturing only: no token, PIN or customer data is sent on this connection.
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            val code = connection.responseCode
            if (code !in 200..299) throw ApiException.network()
            trustManager.capturedFingerprint ?: throw ApiException.network()
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException.network("Could not reach the main phone at that address")
        } finally {
            connection?.disconnect()
        }
    }

    fun forget() {
        secureStore.remove(SecureStore.KEY_DEVICE_TOKEN)
        prefs.clearConnection()
        prefs.configured = false
        prefs.onboardingDone = false
    }
}
