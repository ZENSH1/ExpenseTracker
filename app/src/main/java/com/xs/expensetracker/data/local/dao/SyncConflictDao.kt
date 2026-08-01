package com.xs.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {

    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflicts")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM sync_conflicts")
    suspend fun getAll(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id")
    suspend fun getById(id: String): SyncConflictEntity?

    /** Ids of conflicted records, so the push phase can skip them until the user decides. */
    @Query("SELECT entityId FROM sync_conflicts WHERE entityType = :entityType")
    suspend fun conflictedIdsOfType(entityType: String): List<String>

    @Upsert
    suspend fun upsert(conflict: SyncConflictEntity)

    @Upsert
    suspend fun upsertAll(conflicts: List<SyncConflictEntity>)

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sync_conflicts")
    suspend fun deleteAll()
}
