package com.slncloth.shop.core.money

import java.util.Locale

/**
 * Pricing rules for the shop.
 *
 * Markup and cashback are stored in basis points (bp):
 *   20%  = 2000 bp, 5% = 500 bp.
 *
 * Definitions used in reports (documented in the owner guide):
 *   - sales    = sum of bill subtotals (gross, before wallet redemption)
 *   - cost     = sum of buying costs of sold items
 *   - profit   = sales - cost
 *   - markup   = profit / cost          (profit divided by cost)
 *   - margin   = profit / sales         (profit divided by selling price)
 *
 * A 20% cost markup on a ₹400 item gives ₹480, which is a 16.7% margin — never the same number.
 */
object Pricing {

    const val BASIS_POINTS_PER_UNIT = 10_000L

    /** Suggested selling price, rounded half-up to the nearest paise. */
    fun suggestedPricePaise(costPaise: Long, markupBp: Int): Long {
        if (costPaise <= 0L) return 0L
        val factor = BASIS_POINTS_PER_UNIT + markupBp.coerceAtLeast(0)
        return (costPaise * factor + (BASIS_POINTS_PER_UNIT / 2)) / BASIS_POINTS_PER_UNIT
    }

    /**
     * Cashback = rate % of the amount paid after wallet use, rounded to the nearest rupee.
     * The returned value is always a whole number of rupees (paise are zero).
     */
    fun cashbackPaise(netPaidPaise: Long, cashbackBp: Int): Long {
        if (netPaidPaise <= 0L || cashbackBp <= 0) return 0L
        val rupees = (netPaidPaise * cashbackBp.toLong() + 500_000L) / 1_000_000L
        return rupees * 100L
    }

    fun profitPaise(sellingPaise: Long, costPaise: Long): Long = sellingPaise - costPaise

    /** Profit divided by cost. */
    fun markupPercent(profitPaise: Long, costPaise: Long): Double =
        if (costPaise <= 0L) 0.0 else profitPaise * 100.0 / costPaise

    /** Profit divided by selling price. */
    fun marginPercent(profitPaise: Long, salesPaise: Long): Double =
        if (salesPaise <= 0L) 0.0 else profitPaise * 100.0 / salesPaise

    fun percentString(value: Double): String = String.format(Locale.US, "%.1f%%", value)

    /** Converts a user-entered percentage (e.g. "20") to basis points. */
    fun percentToBasisPoints(percentText: String): Int? {
        val normalized = percentText.trim().replace("%", "").replace(",", ".")
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < 0.0 || value > 1000.0) return null
        return Math.round(value * 100.0).toInt()
    }

    fun basisPointsToPercentText(bp: Int): String {
        val whole = bp / 100
        val frac = (bp % 100)
        return if (frac == 0) whole.toString() else String.format(Locale.US, "%d.%02d", whole, frac)
    }
}
