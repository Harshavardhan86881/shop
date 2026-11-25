package com.slncloth.shop.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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

/**
 * Authoritative shop database. Lives only on the main phone.
 *
 * Schema history:
 *   v1 – initial tables.
 *   v2 – adds customers.note and an index on bills(created_at); template for future migrations.
 */
@Database(
    entities = [
        SettingsEntity::class,
        CustomerEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        WalletLedgerEntity::class,
        UserEntity::class,
        DeviceEntity::class,
        SessionEntity::class,
        PairingTokenEntity::class,
        AuditEventEntity::class,
        LoginAttemptEntity::class,
        AppMetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao
    abstract fun walletDao(): WalletDao
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun sessionDao(): SessionDao
    abstract fun pairingDao(): PairingDao
    abstract fun auditDao(): AuditDao
    abstract fun loginAttemptDao(): LoginAttemptDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        const val NAME = "shop.db"
    }
}

/** Small local database on every phone holding unsent bill drafts. */
@Database(entities = [com.slncloth.shop.data.db.entity.DraftEntity::class], version = 1, exportSchema = false)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "drafts.db"
    }
}
