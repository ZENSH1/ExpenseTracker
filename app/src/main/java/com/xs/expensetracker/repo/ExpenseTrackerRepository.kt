package com.xs.expensetracker.repo

import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.data.models.TransactionSource
import kotlinx.coroutines.flow.Flow

interface ExpenseTrackerRepository {

    // ── Trackers ──────────────────────────────────────────────────────────
    fun observeTrackers(userId: String): Flow<List<Tracker>>
    fun observeTracker(trackerId: String): Flow<Tracker?>
    suspend fun createTracker(name: String, ownerId: String): Result<String>
    suspend fun updateTrackerName(trackerId: String, newName: String): Result<Unit>
    suspend fun shareTracker(trackerId: String, userIdToShare: String): Result<Unit>
    suspend fun deleteTracker(trackerId: String): Result<Unit>

    // ── Sources ───────────────────────────────────────────────────────────
    fun observeSources(trackerId: String, type: TransactionType? = null): Flow<List<TransactionSource>>
    suspend fun createSource(trackerId: String, name: String, type: TransactionType): Result<String>
    suspend fun deleteSource(trackerId: String, sourceId: String): Result<Unit>

    // ── Receipts ──────────────────────────────────────────────────────────
    // sourceId = null  → fetch ALL receipts across every source in the tracker
    // sourceId = ""    → same as null (treated as "all")
    // sourceId = "xyz" → fetch receipts for that specific source only
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
    suspend fun updateReceipt(trackerId: String, sourceId: String, receipt: TransactionReceipt): Result<Unit>
    suspend fun deleteReceipt(trackerId: String, sourceId: String, receiptId: String): Result<Unit>
}