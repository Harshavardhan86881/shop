package com.slncloth.shop.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations are pure SQL and must never lose data.
 *
 * v1 -> v2: adds a free-text note to customers and an explicit index on bill creation time.
 * (v1 was the first internal build; this migration is kept as the template for future versions.)
 */
object Migrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE customers ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_created_at ON bills(created_at)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
