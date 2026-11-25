package com.slncloth.shop.client

import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.security.PinnedTls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Lightweight GET /health used by the server start-up check and the connection monitor. */
object HealthProbe {

    suspend fun check(baseUrl: String, fingerprint: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || fingerprint.isBlank()) return@withContext false
        return@withContext try {
            val url = URL(baseUrl.trimEnd('/') + "/api/v1/health")
            val connection = url.openConnection() as HttpsURLConnection
            connection.sslSocketFactory = PinnedTls.sslContext(fingerprint).socketFactory
            connection.hostnameVerifier = PinnedTls.hostnameVerifier(fingerprint)
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /** Throws [ApiException] with a short, user-presentable reason. */
    suspend fun require(baseUrl: String, fingerprint: String) {
        if (!check(baseUrl, fingerprint)) throw ApiException.network()
    }
}
