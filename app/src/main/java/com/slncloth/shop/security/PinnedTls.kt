package com.slncloth.shop.security

import com.slncloth.shop.core.util.Ids
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * Certificate pinning for the local shop server.
 *
 * The client trusts exactly one certificate — the one whose SHA-256 fingerprint was
 * shown on the main phone and carried in the pairing QR (or verified by the owner in
 * the manual pairing flow). There is no trust-all fallback.
 */
object PinnedTls {

    fun sslContext(fingerprintHex: String): SSLContext {
        val expected = fingerprintHex.trim().lowercase()
        require(expected.length == 64) { "invalid certificate fingerprint" }

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client certificates are not used")
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("No server certificate")
                val actual = Ids.sha256Hex(leaf.encoded)
                if (!Ids.constantTimeEquals(actual, expected)) {
                    throw CertificateException("Server identity did not match")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
    }

    /** Verifies the presented leaf certificate instead of relying on hostname matching. */
    fun hostnameVerifier(fingerprintHex: String): HostnameVerifier {
        val expected = fingerprintHex.trim().lowercase()
        return HostnameVerifier { _, session ->
            try {
                val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                    ?: return@HostnameVerifier false
                Ids.constantTimeEquals(Ids.sha256Hex(leaf.encoded), expected)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Captures the certificate presented by the server without trusting it yet.
     * Used only by the manual pairing flow, before any secret is sent; the owner then
     * compares the displayed fingerprint with the main phone.
     */
    class TofuTrustManager : X509TrustManager {
        @Volatile
        var capturedFingerprint: String? = null

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Client certificates are not used")
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("No server certificate")
            capturedFingerprint = Ids.sha256Hex(leaf.encoded)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun captureContext(): Pair<SSLContext, TofuTrustManager> {
        val manager = TofuTrustManager()
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(manager), SecureRandom())
        }
        return context to manager
    }
}
