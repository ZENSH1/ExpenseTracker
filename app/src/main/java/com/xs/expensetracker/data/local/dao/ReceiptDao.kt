package com.xs.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    // ── Reads (UI) ───────────────────────────────────────────────────────────

    /** [sourceId] `null` or blank returns every receipt in the tracker. */
    @Query(
        """
        SELECT * FROM receipts
        WHERE trackerId = :trackerId
          AND deleted = 0
          AND (:sourceId IS NULL OR :sourceId = '' OR sourceId = :sourceId)
        ORDER BY date DESC, createdAt DESC
        """
    )
    fun observeByTracker(trackerId: String, sourceId: String?): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE trackerId = :trackerId AND deleted = 0 ORDER BY date DESC")
    suspend fun getActiveByTracker(trackerId: String): List<ReceiptEntity>

    // ── Reads (sync) ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getById(receiptId: String): ReceiptEntity?

    @Query("SELECT * FROM receipts")
    suspend fun getAll(): List<ReceiptEntity>

    @Query("SELECT * FROM receipts WHERE pendingSync = 1 ORDER BY updatedAt ASC")
    suspend fun getPending(): List<ReceiptEntity>

    // ── Writes ───────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsert(receipt: ReceiptEntity)

    @Upsert
    suspend fun upsertAll(receipts: List<ReceiptEntity>)

    @Query(
        """
        UPDATE receipts SET pendingSync = 0, remoteUpdatedAt = :pushedUpdatedAt
        WHERE id = :receiptId AND updatedAt = :pushedUpdatedAt
        """
    )
    suspend fun markSynced(receiptId: String, pushedUpdatedAt: Long): Int

    @Query(
        """
        UPDATE receipts SET deleted = 1, pendingSync = 1, updatedAt = :now
        WHERE trackerId = :trackerId AND deleted = 0
        """
    )
    suspend fun softDeleteByTracker(trackerId: String, now: Long)

    @Query(
        """
        UPDATE receipts SET deleted = 1, pendingSync = 1, updatedAt = :now
        WHERE sourceId = :sourceId AND deleted = 0
        """
    )
    suspend fun softDeleteBySource(sourceId: String, now: Long)

    @Query("UPDATE receipts SET pendingSync = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun markAllPending(now: Long)

    @Query("DELETE FROM receipts WHERE trackerId NOT IN (SELECT id FROM trackers)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM receipts WHERE deleted = 1 AND pendingSync = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(before: Long): Int

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()
}
