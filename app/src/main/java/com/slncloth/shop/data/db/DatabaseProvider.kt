package com.slncloth.shop.data.db

import android.content.Context
import androidx.room.Room
import java.io.File

/**
 * Owns the main shop database instance so it can be closed and replaced during restore.
 * All server services obtain DAOs through [get] on every request instead of caching them.
 */
class DatabaseProvider(private val context: Context) {

    @Volatile
    private var instance: AppDatabase? = null

    val databaseFile: File
        get() = context.getDatabasePath(AppDatabase.NAME)

    fun get(): AppDatabase = instance ?: synchronized(this) {
        instance ?: build().also { instance = it }
    }

    private fun build(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    /**
     * Closes the database, deletes WAL/SHM files and copies [snapshot] over the live file.
     * The caller must stop the server first so no request can write during the swap.
     */
    @Synchronized
    fun closeAndReplaceWith(snapshot: File) {
        instance?.close()
        instance = null
        val dbFile = databaseFile
        dbFile.parentFile?.mkdirs()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        if (dbFile.exists()) dbFile.delete()
        snapshot.copyTo(dbFile, overwrite = true)
    }

    /** Replaces the file before any Room instance has been opened (first-run restore). */
    @Synchronized
    fun replaceBeforeOpen(snapshot: File) {
        check(instance == null) { "Database already opened" }
        val dbFile = databaseFile
        dbFile.parentFile?.mkdirs()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        if (dbFile.exists()) dbFile.delete()
        snapshot.copyTo(dbFile, overwrite = true)
    }

    @Synchronized
    fun close() {
        instance?.close()
        instance = null
    }
}
