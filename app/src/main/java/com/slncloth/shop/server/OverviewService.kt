package com.slncloth.shop.server

import com.slncloth.shop.core.api.BillSummaryDto
import com.slncloth.shop.core.api.CategoryBreakdownDto
import com.slncloth.shop.core.api.DailyPointDto
import com.slncloth.shop.core.api.OverviewResponse
import com.slncloth.shop.core.money.Pricing
import com.slncloth.shop.core.util.Format
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.entity.BillEntity
import com.slncloth.shop.data.db.entity.BillItemEntity

/**
 * Owner-only financial reporting.
 *
 * Definitions:
 *   sales  = sum of ACTIVE bill subtotals (gross, before wallet redemption)
 *   cost   = sum of item buying costs
 *   profit = sales - cost
 *   markup = profit / cost
 *   margin = profit / sales
 *
 * Wallet redemption is a payment method, not a discount: it does not reduce sales or profit.
 * Cashback is recorded as issued and shown separately; it is not subtracted from profit
 * (the shop can change this policy later, but the definition must stay explicit).
 */
class OverviewService(private val dbProvider: DatabaseProvider) {

    private class DayAccumulator {
        var sales: Long = 0
        var cost: Long = 0
        var cashback: Long = 0
    }

    suspend fun overview(from: Long, to: Long): OverviewResponse {
        val db = dbProvider.get()
        val bills = db.billDao().between(from, to)
        val active = bills.filter { it.status == BillStatus.ACTIVE }
        val ids = active.map { it.id }
        val items: List<BillItemEntity> = if (ids.isEmpty()) emptyList() else db.billItemDao().forBills(ids)

        val costByBill = items.groupBy { it.billId }
            .mapValues { (_, list) -> list.sumOf { it.costPaise * it.quantity } }

        val sales = active.sumOf { it.subtotalPaise }
        val cost = costByBill.values.sum()
        val profit = sales - cost
        val cashbackIssued = active.sumOf { it.cashbackPaise }
        val walletRedeemed = active.sumOf { it.walletUsedPaise }

        val byDay = linkedMapOf<String, DayAccumulator>()
        active.forEach { bill ->
            val key = Format.dayKey(bill.createdAt)
            val acc = byDay.getOrPut(key) { DayAccumulator() }
            acc.sales += bill.subtotalPaise
            acc.cost += costByBill[bill.id] ?: 0L
            acc.cashback += bill.cashbackPaise
        }

        val byCategory = linkedMapOf<String, Triple<Int, Long, Long>>()
        items.forEach { item ->
            val previous = byCategory[item.category] ?: Triple(0, 0L, 0L)
            byCategory[item.category] = Triple(
                previous.first + item.quantity,
                previous.second + item.lineTotalPaise,
                previous.third + item.costPaise * item.quantity,
            )
        }

        val customers = HashMap<String, Pair<String, String>>()
        fun customerOf(bill: BillEntity): Pair<String, String> {
            return customers.getOrPut(bill.customerId) {
                val c = db.customerDao().getById(bill.customerId)
                (c?.name ?: "Unknown customer") to (c?.mobile ?: "")
            }
        }

        val recent = active.sortedByDescending { it.createdAt }.take(10).map { bill ->
            val (name, mobile) = customerOf(bill)
            BillSummaryDto(
                id = bill.id,
                number = bill.number,
                customerName = name,
                customerMobile = mobile,
                createdAt = bill.createdAt,
                subtotalPaise = bill.subtotalPaise,
                netPaidPaise = bill.netPaidPaise,
                cashbackPaise = bill.cashbackPaise,
                paymentMethod = bill.paymentMethod,
                status = bill.status,
            )
        }

        return OverviewResponse(
            from = from,
            to = to,
            salesPaise = sales,
            costPaise = cost,
            profitPaise = profit,
            markupPercent = Pricing.markupPercent(profit, cost),
            marginPercent = Pricing.marginPercent(profit, sales),
            cashbackIssuedPaise = cashbackIssued,
            walletRedeemedPaise = walletRedeemed,
            billCount = active.size,
            itemCount = items.sumOf { it.quantity },
            daily = byDay.entries.sortedBy { it.key }.map { (day, acc) ->
                DailyPointDto(
                    day = day,
                    salesPaise = acc.sales,
                    costPaise = acc.cost,
                    profitPaise = acc.sales - acc.cost,
                    cashbackPaise = acc.cashback,
                )
            },
            categories = byCategory.entries
                .sortedByDescending { it.value.second }
                .map { (category, value) ->
                    CategoryBreakdownDto(
                        category = category,
                        quantity = value.first,
                        salesPaise = value.second,
                        costPaise = value.third,
                        profitPaise = value.second - value.third,
                    )
                },
            recentBills = recent,
        )
    }
}
