package com.xs.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Cross-table queries the sync UI needs but that belong to no single entity. */
@Dao
interface SyncDao {

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM trackers WHERE pendingSync = 1)
             + (SELECT COUNT(*) FROM sources  WHERE pendingSync = 1)
             + (SELECT COUNT(*) FROM receipts WHERE pendingSync = 1)
        """
    )
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM trackers WHERE deleted = 0)
             + (SELECT COUNT(*) FROM sources  WHERE deleted = 0)
             + (SELECT COUNT(*) FROM receipts WHERE deleted = 0)
        """
    )
    suspend fun countActiveRecords(): Int

    /** Everything held on this device, synced or not — what the account-switch prompt counts. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM trackers WHERE deleted = 0)
             + (SELECT COUNT(*) FROM sources  WHERE deleted = 0)
             + (SELECT COUNT(*) FROM receipts WHERE deleted = 0)
        """
    )
    fun observeActiveRecordCount(): Flow<Int>
}
