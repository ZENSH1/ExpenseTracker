package com.xs.expensetracker.domain.repo

import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the local database, which is the source of truth.
 *
 * Every call here succeeds or fails on device alone — none of them touch the network, and none
 * require an account. Writes mark rows for sync and nudge the scheduler; whether that sync ever
 * happens is a separate concern the user controls.
 *
 * Note the absence of a `userId` parameter on the observers: the local database only ever holds
 * this device's data, so scoping is implicit.
 */
interface ExpenseTrackerRepository {

    // ── Trackers ──────────────────────────────────────────────────────────
    fun observeTrackers(): Flow<List<Tracker>>
    fun observeTracker(trackerId: String): Flow<Tracker?>
    suspend fun createTracker(name: String): Result<String>
    suspend fun updateTrackerName(trackerId: String, newName: String): Result<Unit>
    suspend fun shareTracker(trackerId: String, userIdToShare: String): Result<Unit>
    suspend fun deleteTracker(trackerId: String): Result<Unit>

    // ── Sources ───────────────────────────────────────────────────────────
    fun observeSources(trackerId: String, type: TransactionType? = null): Flow<List<TransactionSource>>
    suspend fun createSource(trackerId: String, name: String, type: TransactionType): Result<String>
    suspend fun updateSource(sourceId: String, name: String, type: TransactionType): Result<Unit>
    suspend fun deleteSource(trackerId: String, sourceId: String): Result<Unit>

    // ── Receipts ──────────────────────────────────────────────────────────
    // sourceId null or blank → every receipt in the tracker.
    fun observeReceipts(trackerId: String, sourceId: String?): Flow<List<TransactionReceipt>>
    suspend fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Result<String>
    suspend fun updateReceipt(receipt: TransactionReceipt): Result<Unit>
    suspend fun deleteReceipt(receiptId: String): Result<Unit>

    /** One-shot reads for export, which needs the whole tracker regardless of active filters. */
    suspend fun getSourcesForExport(trackerId: String): List<TransactionSource>
    suspend fun getReceiptsForExport(trackerId: String): List<TransactionReceipt>

    /** Removes every local record. Used when discarding data on an account switch. */
    suspend fun wipeLocalData()
}
