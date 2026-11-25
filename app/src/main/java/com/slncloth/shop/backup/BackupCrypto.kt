package com.slncloth.shop.backup

import com.slncloth.shop.core.util.Ids
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String) : Exception(message)

/**
 * Backup container encryption.
 *
 * Format (all byte order big-endian):
 *   magic "SLNCBK01" (8) | version (1) | iterations (4) | salt (16) | nonce (12) | AES-GCM ciphertext
 *
 * Key derivation: PBKDF2-HMAC-SHA-256, 310,000 iterations, 256-bit key.
 * The passphrase is never stored; forgetting it makes the backup unrecoverable by design.
 * AES-GCM provides confidentiality and integrity — a damaged or tampered file fails to decrypt.
 */
object BackupCrypto {

    const val MAGIC = "SLNCBK01"
    private const val VERSION: Int = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    const val DEFAULT_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000

    fun encrypt(plain: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "unsupported iteration count" }
        val salt = Ids.randomBytes(SALT_BYTES)
        val nonce = Ids.randomBytes(NONCE_BYTES)
        val key = deriveKey(passphrase, salt, iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plain)

        val out = ByteArrayOutputStream(MAGIC.length + 1 + 4 + salt.size + nonce.size + ciphertext.size)
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(VERSION)
        out.write(ByteBuffer.allocate(4).putInt(iterations).array())
        out.write(salt)
        out.write(nonce)
        out.write(ciphertext)
        return out.toByteArray()
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        if (blob.size < MAGIC.length + 1 + 4 + SALT_BYTES + NONCE_BYTES + TAG_BITS / 8) {
            throw BackupException("This file is too small to be a shop backup.")
        }
        val magic = String(blob, 0, MAGIC.length, Charsets.US_ASCII)
        if (magic != MAGIC) throw BackupException("This file is not a shop backup.")
        val version = blob[MAGIC.length].toInt() and 0xFF
        if (version != VERSION) throw BackupException("This backup was made by a newer or unsupported app version.")

        var offset = MAGIC.length + 1
        val iterations = ByteBuffer.wrap(blob, offset, 4).int
        offset += 4
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupException("Backup header is damaged.")
        }
        val salt = blob.copyOfRange(offset, offset + SALT_BYTES)
        offset += SALT_BYTES
        val nonce = blob.copyOfRange(offset, offset + NONCE_BYTES)
        offset += NONCE_BYTES
        val ciphertext = blob.copyOfRange(offset, blob.size)

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Wrong passphrase and tampered data both end here; never reveal which.
            throw BackupException("Wrong passphrase or damaged backup. Current data was not changed.")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val key = factory.generateSecret(spec).encoded
            SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
