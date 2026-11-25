package com.slncloth.shop.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String,
    val address: String,
    val ownerName: String,
    /** Buying-cost markup in basis points. 2000 = 20%. Owner-only. */
    val markupBp: Int,
    /** Cashback in basis points. 500 = 5%. */
    val cashbackBp: Int,
    val allowStaffPriceOverride: Boolean,
    val receiptFooter: String,
    val serverPort: Int,
    /** Preferred bind address. Empty string = choose automatically. */
    val bindAddress: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["mobile"]),
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
    ]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mobile: String,
    val note: String,
    val walletBalancePaise: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["number"], unique = true),
        Index(value = ["request_key"], unique = true),
        Index(value = ["customer_id"]),
        Index(value = ["created_at"]),
        Index(value = ["status"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        )
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val customerId: String,
    val createdAt: Long,
    val subtotalPaise: Long,
    val walletUsedPaise: Long,
    val netPaidPaise: Long,
    val cashbackPaise: Long,
    val paymentMethod: String,
    val status: String,
    /** Client-generated idempotency key. Unique index enforces at most one bill per key. */
    val requestKey: String,
    /** SHA-256 of the canonical business payload, used to detect key reuse with different data. */
    val requestHash: String,
    /** Snapshot of the cashback rate used when the bill was confirmed. */
    val cashbackBp: Int,
    val createdByUserId: String,
    val createdByDeviceId: String,
    val reversalReason: String? = null,
    val reversedAt: Long? = null,
    val reversedByUserId: String? = null,
)

@Entity(
    tableName = "bill_items",
    indices = [Index(value = ["bill_id"])],
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val lineNo: Int,
    val name: String,
    val category: String,
    val quantity: Int,
    /** Buying cost — owner-only, never present in staff/customer DTOs. */
    val costPaise: Long,
    val unitPricePaise: Long,
    val lineTotalPaise: Long,
    val priceOverridden: Boolean,
    /** Snapshot of the markup used when the bill was confirmed. */
    val markupBp: Int,
)

@Entity(
    tableName = "wallet_ledger",
    indices = [Index(value = ["customer_id"]), Index(value = ["bill_id"]), Index(value = ["created_at"])],
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ]
)
data class WalletLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val billId: Long?,
    /** CASHBACK, REDEEM, ADJUSTMENT, REVERSAL */
    val type: String,
    /** CREDIT, DEBIT */
    val direction: String,
    /** Positive magnitude; direction carries the sign. */
    val amountPaise: Long,
    val balanceAfterPaise: Long,
    val reason: String,
    val actorUserId: String,
    val createdAt: Long,
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** OWNER or STAFF. Authorization is enforced server-side from this value. */
    val role: String,
    val pinSalt: ByteArray,
    val pinHash: ByteArray,
    val pinIterations: Int,
    val active: Boolean,
    val createdAt: Long,
    val lastLoginAt: Long?,
) {
    override fun equals(other: Any?): Boolean = other is UserEntity && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Entity(
    tableName = "devices",
    indices = [Index(value = ["token_hash"], unique = true), Index(value = ["revoked_at"])]
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tokenHash: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long?,
    val createdByUserId: String,
)

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["expires_at"]), Index(value = ["device_id"]), Index(value = ["user_id"])]
)
data class SessionEntity(
    @PrimaryKey val tokenHash: String,
    val userId: String,
    val deviceId: String,
    val role: String,
    val createdAt: Long,
    val expiresAt: Long,
)

@Entity(tableName = "pairing_tokens")
data class PairingTokenEntity(
    @PrimaryKey val id: Int = 1,
    val tokenHash: String,
    val createdAt: Long,
    val expiresAt: Long,
)

@Entity(
    tableName = "audit_events",
    indices = [Index(value = ["created_at"]), Index(value = ["entity_type", "entity_id"])]
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val actorUserId: String,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    /** Human-readable JSON summary. Never include PINs, secret codes or tokens. */
    val details: String,
)

@Entity(tableName = "login_attempts", indices = [Index(value = ["created_at"])])
data class LoginAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val success: Boolean,
    val deviceId: String?,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Client-side draft of a bill that has not been confirmed by the server yet. */
@androidx.room.Entity(
    tableName = "drafts",
    indices = [Index(value = ["status"]), Index(value = ["updated_at"])]
)
data class DraftEntity(
    @PrimaryKey val id: String,
    val requestKey: String,
    val customerId: String,
    val customerName: String,
    val customerMobile: String,
    val itemsJson: String,
    val walletUsePaise: Long,
    val paymentMethod: String,
    /** LOCAL, SENDING, UNCERTAIN, REJECTED, DONE */
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String?,
    val billId: Long?,
    val billNumber: String?,
)
