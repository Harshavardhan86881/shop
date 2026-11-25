package com.slncloth.shop.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.slncloth.shop.core.api.AppJson
import com.slncloth.shop.data.db.DatabaseProvider
import com.slncloth.shop.data.db.entity.AppMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.sql.SQLiteDatabase

@Serializable
data class BackupHeader(
    val formatVersion: Int = 1,
    val appVersion: String,
    val dbVersion: Int,
    val createdAt: Long,
    val customers: Int,
    val bills: Int,
    val ledgerEntries: Int,
    val users: Int,
    val shopName: String,
    val ownerName: String,
)

data class BackupInfo(
    val createdAt: Long,
    val fileName: String,
    val sizeBytes: Long,
    val customers: Int,
    val bills: Int,
    val ledgerEntries: Int,
)

data class RestorePreview(
    val header: BackupHeader,
    val tempFile: File,
    val integrityOk: Boolean,
)

/**
 * Creates and restores encrypted, internally consistent database snapshots.
 *
 * The snapshot is produced with SQLite's VACUUM INTO, which is documented to create a
 * consistent snapshot of the original database even while readers are active. Writes are
 * not blocked for long, and the snapshot always contains a complete committed state.
 */
class BackupManager(
    private val context: Context,
    private val dbProvider: DatabaseProvider,
    private val json: Json = AppJson.json,
) {

    companion object {
        private const val TAG = "BackupManager"
        private const val META_LAST_AT = "backup_last_at"
        private const val META_LAST_NAME = "backup_last_name"
        private const val META_LAST_SIZE = "backup_last_size"
        private const val META_LAST_CUSTOMERS = "backup_last_customers"
        private const val META_LAST_BILLS = "backup_last_bills"
        private const val META_LAST_LEDGER = "backup_last_ledger"
        private const val MAX_BACKUP_BYTES = 256L * 1024L * 1024L
        private const val APP_VERSION = "1.0.0"
        private const val DB_VERSION = 2
    }

    // ------------------------------------------------------------------ create

    suspend fun create(uri: Uri, passphrase: CharArray, fileName: String): BackupInfo = withContext(Dispatchers.IO) {
        if (passphrase.size < 8) {
            throw BackupException("Use a backup passphrase of at least 8 characters.")
        }
        val snapshot = File(context.cacheDir, "backup-snapshot.db")
        snapshot.delete()

        val db = dbProvider.get()
        val customers = db.customerDao().countActive()
        val bills = db.billDao().countAll()
        val ledgerEntries = db.walletDao().countAll()
        val users = db.userDao().all().size
        val settings = db.settingsDao().get()
        val owner = db.userDao().all().firstOrNull { it.role == "OWNER" }

        // 1. Consistent snapshot.
        try {
            val path = snapshot.absolutePath.replace("'", "''")
            db.openHelper.writableDatabase.query("VACUUM INTO '$path'").use { it.moveToFirst() }
        } catch (e: Exception) {
            // Very old SQLite builds without VACUUM INTO: checkpoint then copy.
            Log.w(TAG, "VACUUM INTO unavailable, falling back to checkpoint copy")
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            dbProvider.databaseFile.copyTo(snapshot, overwrite = true)
        }

        // 2. Validate the snapshot before encrypting it.
        val integrity = checkIntegrity(snapshot)
        if (!integrity) {
            snapshot.delete()
            throw BackupException("The database snapshot failed its integrity check. No backup was written.")
        }

        // 3. Build header + plaintext container.
        val header = BackupHeader(
            appVersion = APP_VERSION,
            dbVersion = DB_VERSION,
            createdAt = System.currentTimeMillis(),
            customers = customers,
            bills = bills,
            ledgerEntries = ledgerEntries,
            users = users,
            shopName = settings?.shopName ?: "",
            ownerName = owner?.name ?: settings?.ownerName ?: "",
        )
        val headerBytes = json.encodeToString(BackupHeader.serializer(), header).toByteArray(Charsets.UTF_8)
        val dbBytes = snapshot.readBytes()
        val plain = ByteBuffer.allocate(4 + headerBytes.size + dbBytes.size)
            .putInt(headerBytes.size)
            .put(headerBytes)
            .put(dbBytes)
            .array()

        // 4. Encrypt and write.
        val encrypted = BackupCrypto.encrypt(plain, passphrase)
        writeUri(uri, encrypted)

        // 5. Read back and confirm the magic and length before reporting success.
        val verify = readUri(uri, limit = 16L)
        if (verify.size < BackupCrypto.MAGIC.length ||
            String(verify, 0, BackupCrypto.MAGIC.length, Charsets.US_ASCII) != BackupCrypto.MAGIC
        ) {
            throw BackupException("The backup file could not be read back after writing. Try another location.")
        }

        // 6. Record the real, verified status.
        db.appMetaDao().put(AppMetaEntity(META_LAST_AT, header.createdAt.toString()))
        db.appMetaDao().put(AppMetaEntity(META_LAST_NAME, fileName))
        db.appMetaDao().put(AppMetaEntity(META_LAST_SIZE, encrypted.size.toString()))
        db.appMetaDao().put(AppMetaEntity(META_LAST_CUSTOMERS, customers.toString()))
        db.appMetaDao().put(AppMetaEntity(META_LAST_BILLS, bills.toString()))
        db.appMetaDao().put(AppMetaEntity(META_LAST_LEDGER, ledgerEntries.toString()))

        snapshot.delete()
        BackupInfo(
            createdAt = header.createdAt,
            fileName = fileName,
            sizeBytes = encrypted.size.toLong(),
            customers = customers,
            bills = bills,
            ledgerEntries = ledgerEntries,
        )
    }

    // ------------------------------------------------------------------ inspect + restore

    /** Decrypts and validates a backup without touching the live database. */
    suspend fun inspect(uri: Uri, passphrase: CharArray): RestorePreview = withContext(Dispatchers.IO) {
        val blob = readUri(uri, MAX_BACKUP_BYTES)
        val plain = BackupCrypto.decrypt(blob, passphrase)
        if (plain.size < 4) throw BackupException("Backup file is damaged.")

        val headerLength = ByteBuffer.wrap(plain, 0, 4).int
        if (headerLength <= 0 || headerLength > plain.size - 4) throw BackupException("Backup header is damaged.")
        val headerBytes = plain.copyOfRange(4, 4 + headerLength)
        val header = try {
            json.decodeFromString(BackupHeader.serializer(), String(headerBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("Backup header is damaged.")
        }
        if (header.dbVersion > DB_VERSION) {
            throw BackupException("This backup comes from a newer app version. Update the app first.")
        }
        val dbBytes = plain.copyOfRange(4 + headerLength, plain.size)
        if (dbBytes.size < 100 || String(dbBytes, 0, 15, Charsets.ISO_8859_1) != "SQLite format 3") {
            throw BackupException("The backup does not contain a valid database.")
        }

        val temp = File(context.cacheDir, "restore-${System.currentTimeMillis()}.db")
        temp.writeBytes(dbBytes)
        val integrity = checkIntegrity(temp)
        if (!integrity) {
            temp.delete()
            throw BackupException("The backup database failed its integrity check.")
        }
        // Confirm the snapshot contains a usable shop (settings + owner).
        try {
            SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                val cursor = sqlite.rawQuery(
                    "SELECT (SELECT COUNT(*) FROM settings), (SELECT COUNT(*) FROM users WHERE role = 'OWNER')",
                    null
                )
                cursor.use {
                    if (!it.moveToFirst() || it.getInt(0) < 1 || it.getInt(1) < 1) {
                        temp.delete()
                        throw BackupException("The backup does not contain a complete shop and owner.")
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            temp.delete()
            throw BackupException("The backup could not be opened as a shop database.")
        }

        RestorePreview(header = header, tempFile = temp, integrityOk = true)
    }

    /** Applies a validated snapshot. The caller must stop the server before calling this. */
    fun applyRestore(preview: RestorePreview) {
        dbProvider.closeAndReplaceWith(preview.tempFile)
    }

    /** Applies a validated snapshot during first run, before Room has opened the database. */
    fun applyRestoreBeforeOpen(preview: RestorePreview) {
        dbProvider.replaceBeforeOpen(preview.tempFile)
    }

    suspend fun lastBackup(): BackupInfo? {
        val dao = dbProvider.get().appMetaDao()
        val at = dao.get(META_LAST_AT)?.toLongOrNull() ?: return null
        return BackupInfo(
            createdAt = at,
            fileName = dao.get(META_LAST_NAME) ?: "",
            sizeBytes = dao.get(META_LAST_SIZE)?.toLongOrNull() ?: 0L,
            customers = dao.get(META_LAST_CUSTOMERS)?.toIntOrNull() ?: 0,
            bills = dao.get(META_LAST_BILLS)?.toIntOrNull() ?: 0,
            ledgerEntries = dao.get(META_LAST_LEDGER)?.toIntOrNull() ?: 0,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun checkIntegrity(file: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun writeUri(uri: Uri, bytes: ByteArray) {
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw BackupException("Could not open that location for writing.")
        output.use { it.write(bytes) }
    }

    private fun readUri(uri: Uri, limit: Long): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupException("Could not open that backup file.")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw BackupException("Backup file is larger than expected.")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    @Suppress("unused")
    private fun writeTemp(bytes: ByteArray, file: File) {
        FileOutputStream(file).use { it.write(bytes) }
    }
}
