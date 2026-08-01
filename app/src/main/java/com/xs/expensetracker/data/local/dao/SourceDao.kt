package com.xs.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.SourceWithTotal
import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    // ── Reads (UI) ───────────────────────────────────────────────────────────

    /** [type] `null` returns every source in the tracker regardless of kind. */
    @Query(
        """
        SELECT s.*,
               COALESCE((SELECT SUM(r.amount) FROM receipts r
                         WHERE r.sourceId = s.id AND r.deleted = 0), 0.0) AS totalAmount
        FROM sources s
        WHERE s.trackerId = :trackerId
          AND s.deleted = 0
          AND (:type IS NULL OR s.type = :type)
        ORDER BY s.createdAt DESC
        """
    )
    fun observeByTracker(trackerId: String, type: TransactionType?): Flow<List<SourceWithTotal>>

    @Query(
        """
        SELECT s.*,
               COALESCE((SELECT SUM(r.amount) FROM receipts r
                         WHERE r.sourceId = s.id AND r.deleted = 0), 0.0) AS totalAmount
        FROM sources s
        WHERE s.id = :sourceId AND s.deleted = 0
        """
    )
    suspend fun getWithTotal(sourceId: String): SourceWithTotal?

    // ── Reads (sync) ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun getById(sourceId: String): SourceEntity?

    @Query("SELECT * FROM sources")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE trackerId = :trackerId AND deleted = 0")
    suspend fun getActiveByTracker(trackerId: String): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE pendingSync = 1 ORDER BY updatedAt ASC")
    suspend fun getPending(): List<SourceEntity>

    // ── Writes ───────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Upsert
    suspend fun upsertAll(sources: List<SourceEntity>)

    @Query(
        """
        UPDATE sources SET pendingSync = 0, remoteUpdatedAt = :pushedUpdatedAt
        WHERE id = :sourceId AND updatedAt = :pushedUpdatedAt
        """
    )
    suspend fun markSynced(sourceId: String, pushedUpdatedAt: Long): Int

    /** Cascade half of a tracker delete. */
    @Query(
        """
        UPDATE sources SET deleted = 1, pendingSync = 1, updatedAt = :now
        WHERE trackerId = :trackerId AND deleted = 0
        """
    )
    suspend fun softDeleteByTracker(trackerId: String, now: Long)

    @Query("UPDATE sources SET pendingSync = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun markAllPending(now: Long)

    /**
     * Sources whose tracker no longer exists locally. Sync pulls can legitimately deliver a
     * child before its parent, so this only runs after a full reconcile has settled.
     */
    @Query("DELETE FROM sources WHERE trackerId NOT IN (SELECT id FROM trackers)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM sources WHERE deleted = 1 AND pendingSync = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(before: Long): Int

    @Query("DELETE FROM sources")
    suspend fun deleteAll()
}
