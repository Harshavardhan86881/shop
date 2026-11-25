package com.slncloth.shop.security

import com.slncloth.shop.core.util.Ids
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Owner and staff PINs are never stored in plaintext.
 *
 * PBKDF2-HMAC-SHA-256 with a per-user random 16-byte salt and 310,000 iterations
 * (OWASP-recommended range for PBKDF2 in 2024+). The iteration count is stored with
 * the hash so it can be raised later without invalidating existing accounts.
 */
object PinHasher {

    const val DEFAULT_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    class PinHash(val salt: ByteArray, val hash: ByteArray, val iterations: Int)

    fun hash(pin: CharArray, salt: ByteArray = Ids.randomBytes(SALT_BYTES)): PinHash {
        val iterations = DEFAULT_ITERATIONS
        val key = derive(pin, salt, iterations)
        return PinHash(salt, key, iterations)
    }

    fun verify(pin: CharArray, salt: ByteArray, iterations: Int, expected: ByteArray): Boolean {
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
        if (salt.size < 8) return false
        val computed = try {
            derive(pin, salt, iterations, expected.size * 8)
        } catch (e: Exception) {
            return false
        }
        return MessageDigest.isEqual(computed, expected)
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int, keyBits: Int = KEY_BITS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, keyBits)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
