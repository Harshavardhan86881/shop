package com.slncloth.shop.server

import androidx.room.withTransaction
import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.WalletAdjustRequest
import com.slncloth.shop.core.api.WalletEntryDto
import com.slncloth.shop.core.api.WalletMismatch
import com.slncloth.shop.core.api.WalletSummaryDto
import com.slncloth.shop.core.api.WalletVerifyResponse
import com.slncloth.shop.core.util.Validation
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.WalletEntryRow
import com.slncloth.shop.data.db.entity.WalletLedgerEntity

class WalletService(
    private val dbProvider: DatabaseProvider,
    private val audit: AuditLogger,
) {

    suspend fun summary(): WalletSummaryDto {
        val db = dbProvider.get()
        val credits = db.walletDao().totalCredits()
        val debits = db.walletDao().totalDebits()
        val outstanding = db.customerDao().totalWalletBalance()
        val cashbackIssued = db.billDao().totalCashbackIssued()
        val cashbackRedeemed = db.billDao().totalWalletRedeemed()
        return WalletSummaryDto(
            outstandingPaise = outstanding,
            totalCreditsPaise = credits,
            totalDebitsPaise = debits,
            cashbackIssuedPaise = cashbackIssued,
            cashbackRedeemedPaise = cashbackRedeemed,
            entryCount = db.walletDao().countAll(),
        )
    }

    suspend fun entriesForCustomer(customerId: String, limit: Int = 100): List<WalletEntryDto> {
        val db = dbProvider.get()
        val customer = db.customerDao().getById(customerId) ?: throw ApiException.notFound("Customer not found")
        val actorNames = db.userDao().all().associate { it.id to it.name }
        return db.walletDao().forCustomer(customerId, limit.coerceIn(1, 500))
            .map { it.toDto(customer.name, actorNames) }
    }

    suspend fun recent(limit: Int = 100): List<WalletEntryDto> {
        val db = dbProvider.get()
        val names = db.customerDao().allActive().associate { it.id to it.name }
        val actorNames = db.userDao().all().associate { it.id to it.name }
        return db.walletDao().recent(limit.coerceIn(1, 500))
            .map { it.toDto(names[it.entry.customerId] ?: "Unknown customer", actorNames) }
    }

    /** Owner-only wallet correction (credit or debit). Debits cannot overdraw the wallet. */
    suspend fun adjust(ctx: AuthContext, req: WalletAdjustRequest): WalletEntryDto {
        ctx.requireOwner()
        if (!Validation.validReason(req.reason)) throw ApiException.badRequest("Enter a reason of 3 to 300 characters")
        if (req.direction != Directions.CREDIT && req.direction != Directions.DEBIT) {
            throw ApiException.badRequest("Direction must be CREDIT or DEBIT")
        }
        if (req.amountPaise <= 0L || req.amountPaise > MAX_ADJUSTMENT_PAISE) {
            throw ApiException.unprocessable("Amount is out of range", "INVALID_AMOUNT")
        }
        val db = dbProvider.get()
        val now = System.currentTimeMillis()

        val entryId = db.withTransaction {
            val customer = db.customerDao().getById(req.customerId)
                ?: throw ApiException.notFound("Customer not found")
            var balance = customer.walletBalancePaise
            if (req.direction == Directions.DEBIT) {
                if (balance < req.amountPaise) {
                    throw ApiException.conflict("The wallet has less than this amount", "WALLET_INSUFFICIENT")
                }
                balance -= req.amountPaise
            } else {
                balance += req.amountPaise
            }
            val id = db.walletDao().insert(
                WalletLedgerEntity(
                    customerId = customer.id,
                    billId = null,
                    type = LedgerTypes.ADJUSTMENT,
                    direction = req.direction,
                    amountPaise = req.amountPaise,
                    balanceAfterPaise = balance,
                    reason = req.reason.trim(),
                    actorUserId = ctx.userId,
                    createdAt = now,
                )
            )
            db.customerDao().updateWallet(customer.id, balance, now)
            db.auditDao().insert(
                com.slncloth.shop.data.db.entity.AuditEventEntity(
                    createdAt = now,
                    actorUserId = ctx.userId,
                    actorName = ctx.userName,
                    action = "WALLET_ADJUSTED",
                    entityType = "customer",
                    entityId = customer.id,
                    details = "direction=${req.direction}; amountPaise=${req.amountPaise}; reason=${req.reason.trim()}",
                )
            )
            id
        }

        val customerName = db.customerDao().getById(req.customerId)?.name ?: "Unknown customer"
        val actorNames = db.userDao().all().associate { it.id to it.name }
        return db.walletDao().forCustomer(req.customerId, 1).firstOrNull()
            ?.toDto(customerName, actorNames)
            ?: throw ApiException.server("Wallet entry could not be read back")
    }

    /** Verifies every wallet balance against its append-only ledger. Owner-only. */
    suspend fun verify(): WalletVerifyResponse {
        val db = dbProvider.get()
        val balances = db.walletDao().customerBalances()
        val ledger = db.walletDao().balancesFromLedger().associate { it.customerId to it.balance }
        val names = db.customerDao().allActive().associate { it.id to it.name }
        val mismatches = balances.mapNotNull { row ->
            val ledgerBalance = ledger[row.id] ?: 0L
            if (ledgerBalance == row.balance) {
                null
            } else {
                WalletMismatch(
                    customerId = row.id,
                    customerName = names[row.id] ?: "Unknown customer",
                    balancePaise = row.balance,
                    ledgerPaise = ledgerBalance,
                )
            }
        }
        return WalletVerifyResponse(ok = mismatches.isEmpty(), mismatches = mismatches)
    }

    private fun WalletEntryRow.toDto(customerName: String, actorNames: Map<String, String>) = WalletEntryDto(
        id = entry.id,
        customerId = entry.customerId,
        customerName = customerName,
        billId = entry.billId,
        billNumber = billNumber,
        type = entry.type,
        direction = entry.direction,
        amountPaise = entry.amountPaise,
        balanceAfterPaise = entry.balanceAfterPaise,
        reason = entry.reason,
        actorName = actorNames[entry.actorUserId] ?: "System",
        createdAt = entry.createdAt,
    )

    companion object {
        const val MAX_ADJUSTMENT_PAISE = 1_000_000_000L
    }
}
