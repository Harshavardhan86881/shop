package com.slncloth.shop.core.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object Ids {

    private val random = SecureRandom()

    fun uuid(): String = UUID.randomUUID().toString()

    fun newBillRequestKey(): String = "bill-" + UUID.randomUUID().toString()

    fun newToken(bytes: Int = 32): String {
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    fun newNumericCode(digits: Int = 6): String {
        val sb = StringBuilder(digits)
        repeat(digits) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = sha256(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    /** Constant-time comparison for hashes and tokens. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
