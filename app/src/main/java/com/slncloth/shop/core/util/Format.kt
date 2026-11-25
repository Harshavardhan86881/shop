package com.slncloth.shop.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Format {

    private val locale = Locale("en", "IN")

    fun dateTime(epochMillis: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(epochMillis))

    fun date(epochMillis: Long): String =
        SimpleDateFormat("dd MMM yyyy", locale).format(Date(epochMillis))

    fun shortDate(epochMillis: Long): String =
        SimpleDateFormat("dd MMM", locale).format(Date(epochMillis))

    fun time(epochMillis: Long): String =
        SimpleDateFormat("hh:mm a", locale).format(Date(epochMillis))

    fun fileStamp(epochMillis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(epochMillis))

    /** yyyy-MM-dd key used for chart grouping. */
    fun dayKey(epochMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return fmt.format(Date(epochMillis))
    }
}

object Validation {

    private val indianMobile = Regex("^[6-9][0-9]{9}$")

    /**
     * Normalises an Indian mobile number. Accepts +91 / 91 prefixes, spaces and dashes.
     * Returns null when the number is not a valid 10-digit Indian mobile.
     */
    fun normalizeMobile(raw: String): String? {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("91") && digits.length == 12) digits = digits.substring(2)
        if (digits.length != 10) return null
        if (!indianMobile.matches(digits)) return null
        return digits
    }

    fun validName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in 2..80
    }

    fun validPin(pin: String): Boolean = pin.length in 4..10 && pin.all { it.isDigit() }

    fun validReason(reason: String): Boolean = reason.trim().length in 3..300

    fun validSearchQuery(q: String): Boolean = q.length <= 80

    fun cleanText(value: String, max: Int): String = value.trim().take(max)
}
