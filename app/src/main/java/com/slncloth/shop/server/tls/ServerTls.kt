package com.slncloth.shop.server.tls

import android.content.Context
import com.slncloth.shop.core.util.Ids
import com.slncloth.shop.security.SecureStore
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Creates and stores the local server's TLS identity.
 *
 * The certificate is self-signed because there is no public CA and no internet access.
 * Clients trust it only after the owner has verified its SHA-256 fingerprint through the
 * pairing QR (full 64 hex chars) or the manual pairing check (first 8 chars).
 *
 * The private key lives in a PKCS#12 file inside app-private storage, encrypted with a
 * random password that is itself protected by the Android Keystore. A rooted device or a
 * full Android backup of app data is outside the threat model; see docs/SECURITY.md.
 */
class ServerTls(context: Context, private val secureStore: SecureStore) {

    private val dir = File(context.filesDir, "server")
    private val keyStoreFile = File(dir, "identity.p12")

    init {
        dir.mkdirs()
        Security.addProvider(BouncyCastleProvider())
    }

    class Identity(
        val keyStoreFile: File,
        val alias: String,
        val password: CharArray,
        val fingerprint: String,
        val certificate: X509Certificate,
    ) {
        fun keyStore(): KeyStore {
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(keyStoreFile).use { ks.load(it, password) }
            return ks
        }
    }

    @Synchronized
    fun ensureIdentity(): Identity {
        loadExisting()?.let { return it }
        return createIdentity()
    }

    fun fingerprint(): String = ensureIdentity().fingerprint

    @Synchronized
    fun resetIdentity() {
        keyStoreFile.delete()
        secureStore.remove(PASSWORD_KEY)
    }

    private fun createIdentity(): Identity {
        val generator = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }
        val keyPair = generator.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - DAY_MILLIS)
        val notAfter = Date(now + 10L * 365L * DAY_MILLIS)
        val subject = X500Name("CN=SLN Shop Local Server,O=Sri Lakshmi Narasimha Cloth and Readymade,C=IN")

        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(
                arrayOf(
                    GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    GeneralName(GeneralName.dnsName, "localhost"),
                )
            )
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))

        val password = Ids.newToken(24).toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(ALIAS, keyPair.private, password, arrayOf(certificate))
        }
        FileOutputStream(keyStoreFile).use { keyStore.store(it, password) }
        secureStore.put(PASSWORD_KEY, String(password))

        return Identity(
            keyStoreFile = keyStoreFile,
            alias = ALIAS,
            password = password,
            fingerprint = Ids.sha256Hex(certificate.encoded),
            certificate = certificate
        )
    }

    private fun loadExisting(): Identity? {
        if (!keyStoreFile.exists()) return null
        val storedPassword = secureStore.get(PASSWORD_KEY) ?: return null
        return try {
            val password = storedPassword.toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")
            FileInputStream(keyStoreFile).use { keyStore.load(it, password) }
            val certificate = keyStore.getCertificate(ALIAS) as? X509Certificate ?: return null
            // Rotate when fewer than 30 days remain; clients must re-pair after rotation.
            if (System.currentTimeMillis() > certificate.notAfter.time - 30L * DAY_MILLIS) return null
            Identity(keyStoreFile, ALIAS, password, Ids.sha256Hex(certificate.encoded), certificate)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val ALIAS = "slncloth-shop-server"
        private const val PASSWORD_KEY = "server_keystore_password"
        private const val DAY_MILLIS = 86_400_000L
    }
}
