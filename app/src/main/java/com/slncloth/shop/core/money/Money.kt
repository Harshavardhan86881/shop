package com.slncloth.shop.core.money

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * All money in the app is stored and transmitted as integer paise (1 rupee = 100 paise).
 * Floating point is only used for display formatting, never for storage or arithmetic.
 */
object Money {

    private val symbols = DecimalFormatSymbols(Locale("en", "IN")).apply {
        monetaryDecimalSeparator = '.'
        groupingSeparator = ','
        currencySymbol = "\u20B9"
    }

    private val rupeesFormat = DecimalFormat("\u20B9 #,##,##0.00", symbols)
    private val plainFormat = DecimalFormat("#,##,##0.00", symbols)
    private val wholeFormat = DecimalFormat("\u20B9 #,##,##0", symbols)

    /** ₹1,234.56 */
    fun format(paise: Long): String = rupeesFormat.format(paise / 100.0)

    /** 1,234.56 without the symbol */
    fun formatPlain(paise: Long): String = plainFormat.format(paise / 100.0)

    /** ₹1,235 — used only for whole-rupee amounts such as cashback. */
    fun formatWhole(paise: Long): String = wholeFormat.format(paise / 100.0)

    fun formatSigned(paise: Long): String =
        if (paise < 0) "-" + format(-paise) else format(paise)

    /** "-₹1,234.00" or "+₹1,234.00" for ledger rows. */
    fun formatLedger(direction: String, amountPaise: Long): String =
        if (direction == "CREDIT") "+" + format(amountPaise) else "-" + format(amountPaise)

    fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value)
}
