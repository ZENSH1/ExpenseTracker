package com.xs.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xs.expensetracker.data.local.entity.TrackerEntity
import com.xs.expensetracker.data.local.entity.TrackerWithTotals
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    // ── Reads (UI) ───────────────────────────────────────────────────────────
    // grandTotal is computed here rather than stored, so it can never disagree with the
    // receipts it summarises — no counter to keep in step, nothing to repair after a
    // partial sync. Tombstoned rows are excluded everywhere the UI reads.

    @Query(
        """
        SELECT t.*,
               COALESCE((SELECT SUM(CASE WHEN r.type = 'INCOME' THEN r.amount ELSE -r.amount END)
                         FROM receipts r
                         WHERE r.trackerId = t.id AND r.deleted = 0), 0.0) AS grandTotal,
               ((SELECT COUNT(*) FROM receipts r2 WHERE r2.trackerId = t.id AND r2.pendingSync = 1)
              + (SELECT COUNT(*) FROM sources s2 WHERE s2.trackerId = t.id AND s2.pendingSync = 1)
              + (CASE WHEN t.pendingSync = 1 THEN 1 ELSE 0 END)) AS pendingCount
        FROM trackers t
        WHERE t.deleted = 0
        ORDER BY t.createdAt DESC
        """
    )
    fun observeAll(): Flow<List<TrackerWithTotals>>

    @Query(
        """
        SELECT t.*,
               COALESCE((SELECT SUM(CASE WHEN r.type = 'INCOME' THEN r.amount ELSE -r.amount END)
                         FROM receipts r
                         WHERE r.trackerId = t.id AND r.deleted = 0), 0.0) AS grandTotal,
               ((SELECT COUNT(*) FROM receipts r2 WHERE r2.trackerId = t.id AND r2.pendingSync = 1)
              + (SELECT COUNT(*) FROM sources s2 WHERE s2.trackerId = t.id AND s2.pendingSync = 1)
              + (CASE WHEN t.pendingSync = 1 THEN 1 ELSE 0 END)) AS pendingCount
        FROM trackers t
        WHERE t.id = :trackerId AND t.deleted = 0
        """
    )
    fun observeById(trackerId: String): Flow<TrackerWithTotals?>

    // ── Reads (sync) ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM trackers WHERE id = :trackerId")
    suspend fun getById(trackerId: String): TrackerEntity?

    @Query("SELECT * FROM trackers")
    suspend fun getAll(): List<TrackerEntity>

    @Query("SELECT * FROM trackers WHERE pendingSync = 1 ORDER BY updatedAt ASC")
    suspend fun getPending(): List<TrackerEntity>

    @Query("SELECT COUNT(*) FROM trackers WHERE deleted = 0")
    suspend fun countActive(): Int

    // ── Writes ───────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsert(tracker: TrackerEntity)

    @Upsert
    suspend fun upsertAll(trackers: List<TrackerEntity>)

    /**
     * Clears the dirty flag only if the row still holds the exact revision that was pushed.
     * If the user edited the tracker while the network call was in flight, `updatedAt` has
     * moved on, no row matches, and the change stays queued for the next sync instead of
     * being silently dropped.
     */
    @Query(
        """
        UPDATE trackers SET pendingSync = 0, remoteUpdatedAt = :pushedUpdatedAt
        WHERE id = :trackerId AND updatedAt = :pushedUpdatedAt
        """
    )
    suspend fun markSynced(trackerId: String, pushedUpdatedAt: Long): Int

    @Query("UPDATE trackers SET pendingSync = 1, updatedAt = :now WHERE deleted = 0")
    suspend fun markAllPending(now: Long)

    /** Tombstones that both sides have seen are safe to drop for good. */
    @Query("DELETE FROM trackers WHERE deleted = 1 AND pendingSync = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(before: Long): Int

    /**
     * Hard delete, no tombstone. Only for rows that must *not* propagate — such as a tracker
     * belonging to a third party that was readable through a previous account's grants.
     */
    @Query("DELETE FROM trackers WHERE id = :trackerId")
    suspend fun deleteById(trackerId: String)

    @Query("DELETE FROM trackers")
    suspend fun deleteAll()
}
