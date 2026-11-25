package com.slncloth.shop.server

import androidx.room.withTransaction
import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.BillDto
import com.slncloth.shop.core.api.BillItemDto
import com.slncloth.shop.core.api.BillItemRequest
import com.slncloth.shop.core.api.BillSummaryDto
import com.slncloth.shop.core.api.CreateBillRequest
import com.slncloth.shop.core.api.ResolveItemRequest
import com.slncloth.shop.core.api.ResolveItemResponse
import com.slncloth.shop.core.api.ReverseBillRequest
import com.slncloth.shop.core.code.DecodeResult
import com.slncloth.shop.core.code.SecretCodeCodec
import com.slncloth.shop.core.money.Pricing
import com.slncloth.shop.core.util.Ids
import com.slncloth.shop.core.util.Validation
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.entity.AuditEventEntity
import com.slncloth.shop.data.db.entity.BillEntity
import com.slncloth.shop.data.db.entity.BillItemEntity
import com.slncloth.shop.data.db.entity.CustomerEntity
import com.slncloth.shop.data.db.entity.SettingsEntity
import com.slncloth.shop.data.db.entity.WalletLedgerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All billing writes go through this service. Every rule (price decoding, markup,
 * override permission, wallet balance, totals, cashback, ledger entries, audit) is
 * enforced here, server-side, inside a single SQLite transaction.
 *
 * The main phone's own UI calls the same HTTP endpoint — there is no direct-DB billing path.
 */
class BillingService(
    private val dbProvider: DatabaseProvider,
    private val audit: AuditLogger,
) {

    private data class Line(
        val lineNo: Int,
        val name: String,
        val category: String,
        val qty: Int,
        val costPaise: Long,
        val suggestedPricePaise: Long,
        val unitPricePaise: Long,
        val lineTotalPaise: Long,
        val priceOverridden: Boolean,
    ) {
        fun toEntity(billId: Long) = BillItemEntity(
            billId = billId,
            lineNo = lineNo,
            name = name,
            category = category,
            quantity = qty,
            costPaise = costPaise,
            unitPricePaise = unitPricePaise,
            lineTotalPaise = lineTotalPaise,
            priceOverridden = priceOverridden,
            markupBp = 0, // replaced by caller with the settings snapshot
        )
    }

    // ------------------------------------------------------------------ item decoding

    suspend fun resolveItem(ctx: AuthContext, req: ResolveItemRequest): ResolveItemResponse {
        val settings = settings()
        val line = buildLine(
            code = req.code,
            categoryRaw = req.category,
            nameRaw = req.name,
            qty = req.qty,
            requestedPrice = req.pricePaise,
            role = ctx.role,
            settings = settings,
            lineNo = 1,
        )
        return ResolveItemResponse(
            category = line.category,
            name = line.name,
            qty = line.qty,
            suggestedPricePaise = line.suggestedPricePaise,
            unitPricePaise = line.unitPricePaise,
            lineTotalPaise = line.lineTotalPaise,
            priceOverridden = line.priceOverridden,
        )
    }

    private fun buildLine(
        code: String,
        categoryRaw: String,
        nameRaw: String?,
        qty: Int,
        requestedPrice: Long?,
        role: Role,
        settings: SettingsEntity,
        lineNo: Int,
    ): Line {
        if (qty !in 1..999) throw ApiException.unprocessable("Quantity must be between 1 and 999", "INVALID_QTY")
        val category = SecretCodeCodec.categories.firstOrNull {
            it.equals(categoryRaw.trim(), ignoreCase = true)
        } ?: throw ApiException.unprocessable("Choose a valid category", "INVALID_CATEGORY")

        val decoded = when (val result = SecretCodeCodec.decode(code)) {
            is DecodeResult.Valid -> result.costPaise
            DecodeResult.Invalid -> throw ApiException.unprocessable(SecretCodeCodec.INVALID_MESSAGE, "INVALID_CODE")
        }

        val suggested = Pricing.suggestedPricePaise(decoded, settings.markupBp)
        val overridden = requestedPrice != null && requestedPrice != suggested
        if (overridden && role != Role.OWNER && !settings.allowStaffPriceOverride) {
            throw ApiException.forbidden("Manual price changes are allowed only for the owner")
        }
        val unitPrice = if (overridden) requestedPrice!! else suggested
        if (unitPrice <= 0L || unitPrice > MAX_UNIT_PRICE_PAISE) {
            throw ApiException.unprocessable("Selling price is out of range", "INVALID_PRICE")
        }
        val name = nameRaw?.trim()?.take(80).orEmpty().ifEmpty { category }
        return Line(
            lineNo = lineNo,
            name = name,
            category = category,
            qty = qty,
            costPaise = decoded,
            suggestedPricePaise = suggested,
            unitPricePaise = unitPrice,
            lineTotalPaise = unitPrice * qty,
            priceOverridden = overridden,
        )
    }

    // ------------------------------------------------------------------ create

    suspend fun createBill(ctx: AuthContext, req: CreateBillRequest): BillDto {
        if (req.requestKey.isBlank() || req.requestKey.length > 80) {
            throw ApiException.badRequest("Missing bill request key")
        }
        if (req.items.isEmpty() || req.items.size > MAX_ITEMS) {
            throw ApiException.unprocessable("Add at least one item to the bill", "INVALID_ITEMS")
        }
        if (req.paymentMethod !in PaymentMethods.allowed) {
            throw ApiException.unprocessable("Choose a valid payment method", "INVALID_PAYMENT_METHOD")
        }
        if (req.walletUsePaise < 0L) {
            throw ApiException.unprocessable("Wallet amount cannot be negative", "INVALID_WALLET")
        }

        val canonical = canonicalPayload(req)
        val requestHash = Ids.sha256Hex(canonical)
        val db = dbProvider.get()

        val billId = db.withTransaction {
            // Idempotency: the same key returns the original bill; a different payload is rejected.
            db.billDao().getByRequestKey(req.requestKey)?.let { existing ->
                if (existing.requestHash != requestHash) {
                    throw ApiException.conflict(
                        "This bill request was already used with different contents",
                        "IDEMPOTENCY_CONFLICT"
                    )
                }
                return@withTransaction existing.id
            }

            val settings = db.settingsDao().get() ?: throw ApiException.server("Shop settings are missing")
            val customer = db.customerDao().getById(req.customerId)
                ?: throw ApiException.notFound("Customer not found")
            val now = System.currentTimeMillis()

            val lines = req.items.mapIndexed { index, item ->
                buildLine(item.code, item.category, item.name, item.qty, item.pricePaise, ctx.role, settings, index + 1)
            }
            val subtotal = lines.sumOf { it.lineTotalPaise }
            val walletUse = req.walletUsePaise
            if (walletUse > subtotal) {
                throw ApiException.unprocessable("Wallet cannot be more than the bill amount", "INVALID_WALLET")
            }
            // Re-read balance inside the write transaction: two phones cannot both spend the same rupee.
            if (walletUse > customer.walletBalancePaise) {
                throw ApiException.conflict("Wallet balance is not enough for this bill", "WALLET_INSUFFICIENT")
            }
            val netPaid = subtotal - walletUse
            val cashback = Pricing.cashbackPaise(netPaid, settings.cashbackBp)
            val number = nextBillNumber(db, now)

            val newBillId = db.billDao().insert(
                BillEntity(
                    number = number,
                    customerId = customer.id,
                    createdAt = now,
                    subtotalPaise = subtotal,
                    walletUsedPaise = walletUse,
                    netPaidPaise = netPaid,
                    cashbackPaise = cashback,
                    paymentMethod = req.paymentMethod,
                    status = BillStatus.ACTIVE,
                    requestKey = req.requestKey,
                    requestHash = requestHash,
                    cashbackBp = settings.cashbackBp,
                    createdByUserId = ctx.userId,
                    createdByDeviceId = ctx.deviceId,
                )
            )
            db.billItemDao().insertAll(
                lines.map { it.toEntity(newBillId).copy(markupBp = settings.markupBp) }
            )

            var balance = customer.walletBalancePaise
            if (walletUse > 0L) {
                balance -= walletUse
                db.walletDao().insert(
                    WalletLedgerEntity(
                        customerId = customer.id,
                        billId = newBillId,
                        type = LedgerTypes.REDEEM,
                        direction = Directions.DEBIT,
                        amountPaise = walletUse,
                        balanceAfterPaise = balance,
                        reason = "Wallet used on bill $number",
                        actorUserId = ctx.userId,
                        createdAt = now,
                    )
                )
            }
            if (cashback > 0L) {
                balance += cashback
                db.walletDao().insert(
                    WalletLedgerEntity(
                        customerId = customer.id,
                        billId = newBillId,
                        type = LedgerTypes.CASHBACK,
                        direction = Directions.CREDIT,
                        amountPaise = cashback,
                        balanceAfterPaise = balance,
                        reason = "Cashback on bill $number",
                        actorUserId = ctx.userId,
                        createdAt = now,
                    )
                )
            }
            db.customerDao().updateWallet(customer.id, balance, now)

            db.auditDao().insert(
                AuditEventEntity(
                    createdAt = now,
                    actorUserId = ctx.userId,
                    actorName = ctx.userName,
                    action = "BILL_CREATED",
                    entityType = "bill",
                    entityId = newBillId.toString(),
                    details = "bill=$number; salesPaise=$subtotal; walletPaise=$walletUse; cashbackPaise=$cashback; items=${lines.size}",
                )
            )
            newBillId
        }

        return getBill(billId)
    }

    // ------------------------------------------------------------------ read

    suspend fun getBill(id: Long): BillDto {
        val db = dbProvider.get()
        val bill = db.billDao().getById(id) ?: throw ApiException.notFound("Bill not found")
        val customer = db.customerDao().getById(bill.customerId)
        val items = db.billItemDao().forBill(id)
        return bill.toDto(customer, items)
    }

    suspend fun listBills(
        query: String,
        from: Long,
        to: Long,
        status: String,
        limit: Int = 50,
    ): List<BillSummaryDto> {
        val q = query.trim()
        if (!Validation.validSearchQuery(q)) throw ApiException.badRequest("Search text is too long")
        val db = dbProvider.get()
        val bills = db.billDao().search(q, from, to, status, limit.coerceIn(1, 200))
        val cache = HashMap<String, CustomerEntity?>()
        return bills.map { bill ->
            val customer = cache.getOrPut(bill.customerId) { db.customerDao().getById(bill.customerId) }
            BillSummaryDto(
                id = bill.id,
                number = bill.number,
                customerName = customer?.name ?: "Unknown customer",
                customerMobile = customer?.mobile ?: "",
                createdAt = bill.createdAt,
                subtotalPaise = bill.subtotalPaise,
                netPaidPaise = bill.netPaidPaise,
                cashbackPaise = bill.cashbackPaise,
                paymentMethod = bill.paymentMethod,
                status = bill.status,
            )
        }
    }

    /** Used after a connection failure: did the server already commit this request key? */
    suspend fun requestStatus(requestKey: String): com.slncloth.shop.core.api.RequestStatusResponse {
        val bill = dbProvider.get().billDao().getByRequestKey(requestKey)
            ?: return com.slncloth.shop.core.api.RequestStatusResponse(state = "NOT_FOUND")
        return com.slncloth.shop.core.api.RequestStatusResponse(state = "COMPLETED", bill = getBill(bill.id))
    }

    // ------------------------------------------------------------------ reversal

    suspend fun reverseBill(ctx: AuthContext, billId: Long, req: ReverseBillRequest): BillDto {
        ctx.requireOwner()
        if (!Validation.validReason(req.reason)) throw ApiException.badRequest("Enter a reason of 3 to 300 characters")
        val db = dbProvider.get()
        val now = System.currentTimeMillis()

        db.withTransaction {
            val bill = db.billDao().getById(billId) ?: throw ApiException.notFound("Bill not found")
            if (bill.status != BillStatus.ACTIVE) throw ApiException.conflict("This bill has already been reversed")
            val customer = db.customerDao().getById(bill.customerId)
                ?: throw ApiException.notFound("Customer not found")

            var balance = customer.walletBalancePaise
            val cashback = bill.cashbackPaise
            val walletUsed = bill.walletUsedPaise

            if (cashback > 0L) {
                if (balance - cashback < 0L && !req.allowNegativeWallet) {
                    throw ApiException.conflict(
                        "The cashback from this bill has already been spent. Reversing it would leave the wallet negative. " +
                            "Confirm again to allow a negative balance, or adjust the wallet first.",
                        "WALLET_INSUFFICIENT"
                    )
                }
                balance -= cashback
                db.walletDao().insert(
                    WalletLedgerEntity(
                        customerId = customer.id,
                        billId = billId,
                        type = LedgerTypes.REVERSAL,
                        direction = Directions.DEBIT,
                        amountPaise = cashback,
                        balanceAfterPaise = balance,
                        reason = "Cashback reversed for bill ${bill.number}: ${req.reason.trim()}",
                        actorUserId = ctx.userId,
                        createdAt = now,
                    )
                )
            }
            if (walletUsed > 0L) {
                balance += walletUsed
                db.walletDao().insert(
                    WalletLedgerEntity(
                        customerId = customer.id,
                        billId = billId,
                        type = LedgerTypes.REVERSAL,
                        direction = Directions.CREDIT,
                        amountPaise = walletUsed,
                        balanceAfterPaise = balance,
                        reason = "Wallet returned for bill ${bill.number}: ${req.reason.trim()}",
                        actorUserId = ctx.userId,
                        createdAt = now,
                    )
                )
            }

            val updated = db.billDao().reverse(billId, req.reason.trim(), now, ctx.userId)
            if (updated != 1) throw ApiException.conflict("This bill has already been reversed")
            db.customerDao().updateWallet(customer.id, balance, now)
            db.auditDao().insert(
                AuditEventEntity(
                    createdAt = now,
                    actorUserId = ctx.userId,
                    actorName = ctx.userName,
                    action = "BILL_REVERSED",
                    entityType = "bill",
                    entityId = billId.toString(),
                    details = "bill=${bill.number}; cashbackReversedPaise=$cashback; walletReturnedPaise=$walletUsed; reason=${req.reason.trim()}",
                )
            )
        }
        return getBill(billId)
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun settings(): SettingsEntity =
        dbProvider.get().settingsDao().get() ?: throw ApiException.server("Shop settings are missing")

    private fun canonicalPayload(req: CreateBillRequest): String {
        val sb = StringBuilder()
        sb.append(req.customerId).append('|')
        req.items.forEach { item ->
            sb.append(item.code).append(',')
                .append(item.category.trim()).append(',')
                .append(item.name?.trim().orEmpty()).append(',')
                .append(item.qty).append(',')
                .append(item.pricePaise ?: -1L).append(';')
        }
        sb.append('|').append(req.walletUsePaise).append('|').append(req.paymentMethod)
        return sb.toString()
    }

    private fun nextBillNumber(db: com.slncloth.shop.data.db.AppDatabase, now: Long): String {
        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date(now))
        var sequence = db.billDao().countAll() + 1
        var candidate = "SLN-$day-" + sequence.toString().padStart(4, '0')
        while (db.billDao().existsNumber(candidate)) {
            sequence++
            candidate = "SLN-$day-" + sequence.toString().padStart(4, '0')
        }
        return candidate
    }

    private fun BillEntity.toDto(
        customer: CustomerEntity?,
        items: List<BillItemEntity>,
    ) = BillDto(
        id = id,
        number = number,
        customerId = customerId,
        customerName = customer?.name ?: "Unknown customer",
        customerMobile = customer?.mobile ?: "",
        createdAt = createdAt,
        subtotalPaise = subtotalPaise,
        walletUsedPaise = walletUsedPaise,
        netPaidPaise = netPaidPaise,
        cashbackPaise = cashbackPaise,
        paymentMethod = paymentMethod,
        status = status,
        createdBy = createdByUserId,
        reversalReason = reversalReason,
        items = items.map {
            BillItemDto(
                lineNo = it.lineNo,
                name = it.name,
                category = it.category,
                qty = it.quantity,
                unitPricePaise = it.unitPricePaise,
                lineTotalPaise = it.lineTotalPaise,
                priceOverridden = it.priceOverridden,
            )
        },
    )

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_UNIT_PRICE_PAISE = 10_000_000_000L // ₹1 crore per unit
    }
}
