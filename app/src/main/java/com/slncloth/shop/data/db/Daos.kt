package com.slncloth.shop.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.slncloth.shop.data.db.entity.AppMetaEntity
import com.slncloth.shop.data.db.entity.AuditEventEntity
import com.slncloth.shop.data.db.entity.BillEntity
import com.slncloth.shop.data.db.entity.BillItemEntity
import com.slncloth.shop.data.db.entity.CustomerEntity
import com.slncloth.shop.data.db.entity.DeviceEntity
import com.slncloth.shop.data.db.entity.LoginAttemptEntity
import com.slncloth.shop.data.db.entity.PairingTokenEntity
import com.slncloth.shop.data.db.entity.SessionEntity
import com.slncloth.shop.data.db.entity.SettingsEntity
import com.slncloth.shop.data.db.entity.UserEntity
import com.slncloth.shop.data.db.entity.WalletLedgerEntity

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE mobile = :mobile AND active = 1 LIMIT 1")
    suspend fun getByMobile(mobile: String): CustomerEntity?

    @Query(
        "SELECT * FROM customers WHERE active = 1 AND (:q = '' OR name LIKE '%' || :q || '%' OR mobile LIKE '%' || :q || '%') " +
            "ORDER BY updated_at DESC LIMIT :limit"
    )
    suspend fun search(q: String, limit: Int = 50): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE active = 1 ORDER BY name COLLATE NOCASE LIMIT :limit")
    suspend fun list(limit: Int = 200): List<CustomerEntity>

    @Query("UPDATE customers SET wallet_balance_paise = :balance, updated_at = :now WHERE id = :id")
    suspend fun updateWallet(id: String, balance: Long, now: Long)

    @Query("SELECT COUNT(*) FROM customers WHERE active = 1")
    suspend fun countActive(): Int

    @Query("SELECT COALESCE(SUM(wallet_balance_paise), 0) FROM customers WHERE active = 1")
    suspend fun totalWalletBalance(): Long

    @Query("SELECT * FROM customers WHERE active = 1")
    suspend fun allActive(): List<CustomerEntity>

    @Query("UPDATE customers SET active = 0, updated_at = :now WHERE id = :id")
    suspend fun deactivate(id: String, now: Long)
}

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE request_key = :key LIMIT 1")
    suspend fun getByRequestKey(key: String): BillEntity?

    @Query("SELECT * FROM bills WHERE number = :number LIMIT 1")
    suspend fun getByNumber(number: String): BillEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bills WHERE number = :number)")
    suspend fun existsNumber(number: String): Boolean

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun countAll(): Int

    @Query(
        "SELECT b.* FROM bills b JOIN customers c ON c.id = b.customer_id " +
            "WHERE (:q = '' OR b.number LIKE '%' || :q || '%' OR c.name LIKE '%' || :q || '%' OR c.mobile LIKE '%' || :q || '%') " +
            "AND b.created_at >= :from AND b.created_at <= :to " +
            "AND (:status = '' OR b.status = :status) " +
            "ORDER BY b.created_at DESC LIMIT :limit"
    )
    suspend fun search(q: String, from: Long, to: Long, status: String, limit: Int): List<BillEntity>

    @Query("SELECT * FROM bills WHERE status = 'ACTIVE' AND created_at >= :from AND created_at <= :to")
    suspend fun activeBetween(from: Long, to: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE created_at >= :from AND created_at <= :to")
    suspend fun between(from: Long, to: Long): List<BillEntity>

    @Query("SELECT COUNT(*) FROM bills WHERE status = 'ACTIVE' AND created_at >= :from AND created_at <= :to")
    suspend fun countActiveBetween(from: Long, to: Long): Int

    @Query("SELECT COALESCE(SUM(cashback_paise), 0) FROM bills WHERE status = 'ACTIVE'")
    suspend fun totalCashbackIssued(): Long

    @Query("SELECT COALESCE(SUM(wallet_used_paise), 0) FROM bills WHERE status = 'ACTIVE'")
    suspend fun totalWalletRedeemed(): Long

    @Query("SELECT COALESCE(SUM(net_paid_paise), 0) FROM bills WHERE status = 'ACTIVE' AND created_at >= :from AND created_at <= :to")
    suspend fun netPaidBetween(from: Long, to: Long): Long

    @Query(
        "UPDATE bills SET status = 'REVERSED', reversal_reason = :reason, reversed_at = :at, reversed_by_user_id = :by " +
            "WHERE id = :id AND status = 'ACTIVE'"
    )
    suspend fun reverse(id: Long, reason: String, at: Long, by: String): Int

    @Query("SELECT COUNT(*) FROM bills WHERE customer_id = :customerId")
    suspend fun countForCustomer(customerId: String): Int

    @Query("SELECT * FROM bills WHERE customer_id = :customerId ORDER BY created_at DESC LIMIT :limit")
    suspend fun forCustomer(customerId: String, limit: Int): List<BillEntity>

    @Query("UPDATE bills SET customer_id = :toId WHERE customer_id = :fromId")
    suspend fun reassignCustomer(fromId: String, toId: String)
}

@Dao
interface BillItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<BillItemEntity>)

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId ORDER BY line_no")
    suspend fun forBill(billId: Long): List<BillItemEntity>

    @Query("SELECT * FROM bill_items WHERE bill_id IN (:billIds) ORDER BY bill_id, line_no")
    suspend fun forBills(billIds: List<Long>): List<BillItemEntity>

    @Query("SELECT COALESCE(SUM(cost_paise), 0) FROM bill_items")
    suspend fun totalCost(): Long
}

data class WalletEntryRow(
    @androidx.room.Embedded val entry: WalletLedgerEntity,
    @androidx.room.ColumnInfo(name = "bill_number") val billNumber: String?,
)

data class CustomerBalanceRow(
    val id: String,
    val balance: Long,
)

data class LedgerBalanceRow(
    val customerId: String,
    val balance: Long,
)

data class DailySalesRow(
    val day: String,
    val sales: Long,
    val cashback: Long,
)

data class DailyCostRow(
    val day: String,
    val cost: Long,
)

data class CategoryBreakdownRow(
    val category: String,
    val quantity: Int,
    val sales: Long,
    val cost: Long,
)

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WalletLedgerEntity): Long

    @Query(
        "SELECT l.*, b.number AS bill_number FROM wallet_ledger l " +
            "LEFT JOIN bills b ON b.id = l.bill_id " +
            "WHERE l.customer_id = :customerId ORDER BY l.created_at DESC, l.id DESC LIMIT :limit"
    )
    suspend fun forCustomer(customerId: String, limit: Int): List<WalletEntryRow>

    @Query(
        "SELECT l.*, b.number AS bill_number FROM wallet_ledger l " +
            "LEFT JOIN bills b ON b.id = l.bill_id " +
            "ORDER BY l.created_at DESC, l.id DESC LIMIT :limit"
    )
    suspend fun recent(limit: Int): List<WalletEntryRow>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_paise ELSE -amount_paise END), 0) " +
            "FROM wallet_ledger WHERE customer_id = :customerId"
    )
    suspend fun ledgerBalance(customerId: String): Long

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_paise ELSE 0 END), 0) FROM wallet_ledger")
    suspend fun totalCredits(): Long

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_paise ELSE 0 END), 0) FROM wallet_ledger")
    suspend fun totalDebits(): Long

    @Query(
        "SELECT customer_id AS customerId, COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_paise ELSE -amount_paise END), 0) AS balance " +
            "FROM wallet_ledger GROUP BY customer_id"
    )
    suspend fun balancesFromLedger(): List<LedgerBalanceRow>

    @Query("SELECT id, wallet_balance_paise AS balance FROM customers WHERE active = 1")
    suspend fun customerBalances(): List<CustomerBalanceRow>

    @Query("UPDATE wallet_ledger SET customer_id = :toId WHERE customer_id = :fromId")
    suspend fun reassignCustomer(fromId: String, toId: String)

    @Query("SELECT COUNT(*) FROM wallet_ledger")
    suspend fun countAll(): Int
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt")
    suspend fun all(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND active = 1")
    suspend fun countOwners(): Int

    @Query("UPDATE users SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("UPDATE users SET last_login_at = :at WHERE id = :id")
    suspend fun touchLogin(id: String, at: Long)
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE token_hash = :tokenHash AND revoked_at IS NULL LIMIT 1")
    suspend fun byTokenHash(tokenHash: String): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY paired_at DESC")
    suspend fun all(): List<DeviceEntity>

    @Query("UPDATE devices SET last_seen_at = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE devices SET revoked_at = :at WHERE id = :id")
    suspend fun revoke(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM devices WHERE revoked_at IS NULL")
    suspend fun countActive(): Int
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE token_hash = :tokenHash LIMIT 1")
    suspend fun byTokenHash(tokenHash: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE token_hash = :tokenHash")
    suspend fun delete(tokenHash: String)

    @Query("DELETE FROM sessions WHERE expires_at < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM sessions WHERE device_id = :deviceId")
    suspend fun deleteForDevice(deviceId: String)

    @Query("DELETE FROM sessions WHERE user_id = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface PairingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: PairingTokenEntity)

    @Query("SELECT * FROM pairing_tokens WHERE id = 1")
    suspend fun get(): PairingTokenEntity?

    @Query("DELETE FROM pairing_tokens")
    suspend fun clear()
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AuditEventEntity>
}

@Dao
interface LoginAttemptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attempt: LoginAttemptEntity)

    @Query("SELECT COUNT(*) FROM login_attempts WHERE success = 0 AND created_at >= :since")
    suspend fun failedSince(since: Long): Int

    @Query("SELECT MAX(created_at) FROM login_attempts WHERE success = 0 AND created_at >= :since")
    suspend fun lastFailureSince(since: Long): Long?

    @Query("DELETE FROM login_attempts WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface AppMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM app_meta WHERE key = :key")
    suspend fun delete(key: String)
}
