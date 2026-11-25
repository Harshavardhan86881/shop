package com.slncloth.shop.server

import androidx.room.withTransaction
import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.core.api.CustomerDetailDto
import com.slncloth.shop.core.api.CustomerDto
import com.slncloth.shop.core.api.CustomerHistoryDto
import com.slncloth.shop.core.api.CreateCustomerRequest
import com.slncloth.shop.core.api.UpdateCustomerRequest
import com.slncloth.shop.core.api.WalletEntryDto
import com.slncloth.shop.core.util.Ids
import com.slncloth.shop.core.util.Validation
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.WalletEntryRow
import com.slncloth.shop.data.db.entity.CustomerEntity

class CustomerRepository(
    private val dbProvider: DatabaseProvider,
    private val audit: AuditLogger,
) {

    suspend fun search(query: String, limit: Int = 50): List<CustomerDto> {
        val q = query.trim()
        if (!Validation.validSearchQuery(q)) throw ApiException.badRequest("Search text is too long")
        val db = dbProvider.get()
        val customers = if (q.isEmpty()) {
            db.customerDao().list(limit.coerceIn(1, 200))
        } else {
            db.customerDao().search(q, limit.coerceIn(1, 200))
        }
        return customers.map { it.toDto() }
    }

    suspend fun byId(id: String): CustomerDto {
        val db = dbProvider.get()
        val customer = db.customerDao().getById(id) ?: throw ApiException.notFound("Customer not found")
        return customer.toDto(db.billDao().countForCustomer(id))
    }

    suspend fun detail(id: String): CustomerDetailDto {
        val db = dbProvider.get()
        val customer = db.customerDao().getById(id) ?: throw ApiException.notFound("Customer not found")
        val bills = db.billDao().forCustomer(id, 20)
        val ledger = db.walletDao().forCustomer(id, 50)
        val actorNames = db.userDao().all().associate { it.id to it.name }
        return CustomerDetailDto(
            customer = customer.toDto(db.billDao().countForCustomer(id), bills.firstOrNull()?.createdAt),
            history = bills.map {
                CustomerHistoryDto(
                    billId = it.id,
                    billNumber = it.number,
                    createdAt = it.createdAt,
                    subtotalPaise = it.subtotalPaise,
                    netPaidPaise = it.netPaidPaise,
                    cashbackPaise = it.cashbackPaise,
                    status = it.status,
                )
            },
            ledger = ledger.map { it.toDto(customer.name, actorNames) },
        )
    }

    suspend fun create(ctx: AuthContext, req: CreateCustomerRequest): CustomerDto {
        val name = Validation.cleanText(req.name, 80)
        val mobile = Validation.normalizeMobile(req.mobile)
            ?: throw ApiException.unprocessable("Enter a valid 10-digit Indian mobile number", "INVALID_MOBILE")
        if (!Validation.validName(name)) throw ApiException.unprocessable("Name must be 2 to 80 characters", "INVALID_NAME")
        val note = Validation.cleanText(req.note, 300)
        val db = dbProvider.get()

        db.customerDao().getByMobile(mobile)?.let { existing ->
            throw ApiException.conflict(
                "This mobile number already belongs to ${existing.name}. Open that customer instead.",
                "DUPLICATE_MOBILE"
            )
        }

        val now = System.currentTimeMillis()
        val entity = CustomerEntity(
            id = Ids.uuid(),
            name = name,
            mobile = mobile,
            note = note,
            walletBalancePaise = 0L,
            createdAt = now,
            updatedAt = now,
        )
        db.customerDao().insert(entity)
        audit.log(ctx, "CUSTOMER_CREATED", "customer", entity.id, "Customer created with mobile $mobile")
        return entity.toDto(0)
    }

    suspend fun update(ctx: AuthContext, id: String, req: UpdateCustomerRequest): CustomerDto {
        val name = Validation.cleanText(req.name, 80)
        val mobile = Validation.normalizeMobile(req.mobile)
            ?: throw ApiException.unprocessable("Enter a valid 10-digit Indian mobile number", "INVALID_MOBILE")
        if (!Validation.validName(name)) throw ApiException.unprocessable("Name must be 2 to 80 characters", "INVALID_NAME")
        val note = Validation.cleanText(req.note, 300)
        val db = dbProvider.get()
        val existing = db.customerDao().getById(id) ?: throw ApiException.notFound("Customer not found")

        db.customerDao().getByMobile(mobile)?.let { other ->
            if (other.id != id) {
                throw ApiException.conflict(
                    "This mobile number already belongs to ${other.name}.",
                    "DUPLICATE_MOBILE"
                )
            }
        }

        val updated = existing.copy(
            name = name,
            mobile = mobile,
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        db.customerDao().update(updated)
        audit.log(ctx, "CUSTOMER_UPDATED", "customer", id, "Customer details updated")
        return updated.toDto(db.billDao().countForCustomer(id))
    }

    /**
     * Owner-only merge for duplicate customer records. Bills and wallet ledger rows are
     * moved to the target, balances are combined and the source is deactivated (never deleted).
     */
    suspend fun merge(ctx: AuthContext, fromId: String, toId: String) {
        ctx.requireOwner()
        if (fromId == toId) throw ApiException.badRequest("Choose two different customers")
        val db = dbProvider.get()
        val now = System.currentTimeMillis()

        db.withTransaction {
            val from = db.customerDao().getById(fromId) ?: throw ApiException.notFound("Source customer not found")
            val to = db.customerDao().getById(toId) ?: throw ApiException.notFound("Target customer not found")

            db.billDao().reassignCustomer(fromId, toId)
            db.walletDao().reassignCustomer(fromId, toId)

            val combined = to.walletBalancePaise + from.walletBalancePaise
            db.customerDao().updateWallet(toId, combined, now)
            db.customerDao().deactivate(fromId, now)

            db.auditDao().insert(
                com.slncloth.shop.data.db.entity.AuditEventEntity(
                    createdAt = now,
                    actorUserId = ctx.userId,
                    actorName = ctx.userName,
                    action = "CUSTOMER_MERGED",
                    entityType = "customer",
                    entityId = fromId,
                    details = "Merged into $toId; wallet ${from.walletBalancePaise} paise moved",
                )
            )
        }
    }

    private fun CustomerEntity.toDto(totalBills: Int = 0, lastBillAt: Long? = null) = CustomerDto(
        id = id,
        name = name,
        mobile = mobile,
        walletBalancePaise = walletBalancePaise,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        totalBills = totalBills,
        lastBillAt = lastBillAt,
    )

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
}
