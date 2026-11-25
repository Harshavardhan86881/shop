package com.slncloth.shop.server

import com.slncloth.shop.core.api.ApiException
import com.slncloth.shop.data.db.AppDatabase
import com.slncloth.shop.data.db.entity.UserEntity

/** Authorization roles. OWNER always satisfies a STAFF requirement. */
enum class Role { OWNER, STAFF }

data class AuthContext(
    val userId: String,
    val userName: String,
    val role: Role,
    val deviceId: String,
    val sessionTokenHash: String,
) {
    fun requireOwner() {
        if (role != Role.OWNER) throw ApiException.forbidden()
    }
}

object Roles {
    fun parse(value: String): Role = if (value == "OWNER") Role.OWNER else Role.STAFF
}

/** Every mutating endpoint writes an audit row. Never put PINs, codes or tokens in [details]. */
class AuditLogger(private val dbProvider: () -> AppDatabase) {

    suspend fun log(
        ctx: AuthContext,
        action: String,
        entityType: String,
        entityId: String,
        details: String,
        now: Long = System.currentTimeMillis(),
    ) {
        insert(now, ctx.userId, ctx.userName, action, entityType, entityId, details)
    }

    /** Used for bootstrap/pairing events that happen before a session exists. */
    suspend fun logSystem(
        action: String,
        entityType: String,
        entityId: String,
        details: String,
        now: Long = System.currentTimeMillis(),
    ) {
        insert(now, "system", "System", action, entityType, entityId, details)
    }

    private suspend fun insert(
        now: Long,
        actorUserId: String,
        actorName: String,
        action: String,
        entityType: String,
        entityId: String,
        details: String,
    ) {
        dbProvider().auditDao().insert(
            com.slncloth.shop.data.db.entity.AuditEventEntity(
                createdAt = now,
                actorUserId = actorUserId,
                actorName = actorName,
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details,
            )
        )
    }
}

object LedgerTypes {
    const val CASHBACK = "CASHBACK"
    const val REDEEM = "REDEEM"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val REVERSAL = "REVERSAL"
}

object Directions {
    const val CREDIT = "CREDIT"
    const val DEBIT = "DEBIT"
}

object BillStatus {
    const val ACTIVE = "ACTIVE"
    const val REVERSED = "REVERSED"
}

object PaymentMethods {
    val allowed = listOf("Cash", "UPI", "Other")
}

/** Verifies a PIN against this user's salted PBKDF2 hash. */
fun UserEntity.pinMatches(pin: CharArray): Boolean =
    com.slncloth.shop.security.PinHasher.verify(pin, pinSalt, pinIterations, pinHash)
