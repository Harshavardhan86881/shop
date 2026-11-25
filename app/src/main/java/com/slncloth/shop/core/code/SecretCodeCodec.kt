package com.slncloth.shop.core.code

/**
 * Decodes the shop's secret item code into a buying cost.
 *
 * Default mapping (case-sensitive, never conflated):
 *   D=1  P=2  A=3  N=4  I=5  K=6  U=7  M=8  lowercase a=9  R=0
 *
 * Spaces are ignored so "N R R" means 4-0-0 = ₹400.
 * Any character outside the mapping is rejected. Error messages must never
 * include the submitted code or its decoded digits.
 */
object SecretCodeCodec {

    const val MAX_DIGITS = 7
    const val MAX_INPUT_LENGTH = 16

    private val mapping: Map<Char, Int> = mapOf(
        'D' to 1,
        'P' to 2,
        'A' to 3,
        'N' to 4,
        'I' to 5,
        'K' to 6,
        'U' to 7,
        'M' to 8,
        'a' to 9,
        'R' to 0,
    )

    /** Categories shown on New Bill. */
    val categories: List<String> = listOf(
        "Saree", "Shirt", "Jeans", "Kids Wear", "Kurti",
        "T-Shirt", "Towel/Lungi", "Innerwear", "Other"
    )

    fun decode(raw: String): DecodeResult {
        if (raw.length > MAX_INPUT_LENGTH) return DecodeResult.Invalid
        val cleaned = raw.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return DecodeResult.Invalid
        if (cleaned.length > MAX_DIGITS) return DecodeResult.Invalid
        for (ch in cleaned) {
            if (ch !in mapping) return DecodeResult.Invalid
        }
        val costRupees = cleaned.fold(0L) { acc, ch -> acc * 10L + mapping.getValue(ch) }
        if (costRupees <= 0L) return DecodeResult.Invalid
        val costPaise = costRupees * 100L
        // Sanity guard: reject absurd values rather than overflowing downstream maths.
        if (costPaise > 100_000_000_000L) return DecodeResult.Invalid
        return DecodeResult.Valid(costPaise)
    }

    /** Generic, non-revealing message used by UI and server responses. */
    const val INVALID_MESSAGE = "That item code is not recognised. Check the code and try again."

    fun mask(input: String): String = buildString {
        repeat(input.length) { append('\u2022') }
    }
}

sealed interface DecodeResult {
    data class Valid(val costPaise: Long) : DecodeResult
    data object Invalid : DecodeResult
}
