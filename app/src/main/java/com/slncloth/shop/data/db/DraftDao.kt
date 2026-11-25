package com.slncloth.shop.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.slncloth.shop.data.db.entity.DraftEntity

@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun byId(id: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE request_key = :requestKey LIMIT 1")
    suspend fun byRequestKey(requestKey: String): DraftEntity?

    @Query("SELECT * FROM drafts ORDER BY updated_at DESC")
    suspend fun all(): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE status IN ('UNCERTAIN', 'SENDING') ORDER BY updated_at DESC")
    suspend fun needingReconcile(): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE status IN ('LOCAL', 'REJECTED') ORDER BY updated_at DESC")
    suspend fun editable(): List<DraftEntity>

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM drafts WHERE status = 'DONE' AND updated_at < :before")
    suspend fun deleteOldDone(before: Long): Int

    @Query("SELECT COUNT(*) FROM drafts WHERE status IN ('UNCERTAIN', 'SENDING')")
    suspend fun countNeedingReconcile(): Int
}
