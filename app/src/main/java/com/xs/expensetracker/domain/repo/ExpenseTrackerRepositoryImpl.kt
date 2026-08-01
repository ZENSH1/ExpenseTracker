package com.xs.expensetracker.domain.repo

import androidx.room.withTransaction
import com.xs.expensetracker.data.local.ExpenseDatabase
import com.xs.expensetracker.data.local.dao.ReceiptDao
import com.xs.expensetracker.data.local.dao.SourceDao
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.TrackerDao
import com.xs.expensetracker.data.local.entity.ConflictEntityType
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.local.entity.TrackerEntity
import com.xs.expensetracker.data.local.toDomain
import com.xs.expensetracker.data.prefs.IdentityProvider
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource
import com.xs.expensetracker.utils.FirebaseConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local-first implementation.
 *
 * Ids are generated on device (UUIDs) rather than handed out by Firestore. That is what makes
 * offline creation work at all, and it means a record keeps the same identity whether it was
 * created online or off — no id remapping when a device finally syncs.
 *
 * Deletes are soft: a row is tombstoned so the deletion can propagate. Tombstones are swept up
 * by the sync engine once every device has had a chance to see them.
 */
class ExpenseTrackerRepositoryImpl(
    private val database: ExpenseDatabase,
    private val trackerDao: TrackerDao,
    private val sourceDao: SourceDao,
    private val receiptDao: ReceiptDao,
    private val conflictDao: SyncConflictDao,
    private val identity: IdentityProvider,
    private val onLocalChange: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : ExpenseTrackerRepository {

    /**
     * Deleting a record settles any conflict it was caught up in.
     *
     * Conflicted rows are held back from the push queue, so without this a deletion of a
     * conflicted record would sit on the device forever — and the conflict screen would keep
     * offering the user two versions of something they have already thrown away.
     */
    private suspend fun clearConflict(type: ConflictEntityType, entityId: String) {
        conflictDao.deleteById(SyncConflictEntity.keyOf(type, entityId))
    }

    // ── Trackers ─────────────────────────────────────────────────────────────

    override fun observeTrackers(): Flow<List<Tracker>> =
        trackerDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeTracker(trackerId: String): Flow<Tracker?> =
        trackerDao.observeById(trackerId).map { it?.toDomain() }

    override suspend fun createTracker(name: String): Result<String> = mutate {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { FirebaseConst.TRACK_NAME_CANNOT_BE_EMPTY }

        // Owner is the signed-in uid when there is one, otherwise this device's local id. The
        // sync engine rewrites the latter to a real uid when the user eventually signs in.
        val owner = identity.currentOwnerId()
        val stamp = now()
        val id = UUID.randomUUID().toString()

        trackerDao.upsert(
            TrackerEntity(
                id = id,
                name = trimmed,
                ownerId = owner,
                sharedWith = listOf(owner),
                createdAt = stamp,
                updatedAt = stamp,
                pendingSync = true
            )
        )
        id
    }

    override suspend fun updateTrackerName(trackerId: String, newName: String): Result<Unit> = mutate {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { FirebaseConst.TRACK_NAME_CANNOT_BE_EMPTY }
        val existing = trackerDao.getById(trackerId)?.takeUnless { it.deleted }
            ?: throw IllegalStateException(FirebaseConst.TRACK_NOT_FOUND)

        trackerDao.upsert(existing.copy(name = trimmed, updatedAt = now(), pendingSync = true))
    }

    override suspend fun shareTracker(trackerId: String, userIdToShare: String): Result<Unit> = mutate {
        val target = userIdToShare.trim()
        require(target.isNotBlank()) { "User ID cannot be empty" }
        val existing = trackerDao.getById(trackerId)?.takeUnless { it.deleted }
            ?: throw IllegalStateException(FirebaseConst.TRACK_NOT_FOUND)
        require(target !in existing.sharedWith) { FirebaseConst.USER_ALREADY_SHARED }

        trackerDao.upsert(
            existing.copy(
                sharedWith = existing.sharedWith + target,
                updatedAt = now(),
                pendingSync = true
            )
        )
    }

    /**
     * Tombstones the tracker and everything under it in one transaction, so a crash midway
     * cannot leave sources and receipts stranded without a parent.
     */
    override suspend fun deleteTracker(trackerId: String): Result<Unit> = mutate {
        val existing = trackerDao.getById(trackerId) ?: throw IllegalStateException(FirebaseConst.TRACK_NOT_FOUND)
        val stamp = now()

        database.withTransaction {
            receiptDao.softDeleteByTracker(trackerId, stamp)
            sourceDao.softDeleteByTracker(trackerId, stamp)
            trackerDao.upsert(existing.copy(deleted = true, updatedAt = stamp, pendingSync = true))
        }
        clearConflict(ConflictEntityType.TRACKER, trackerId)
    }

    // ── Sources ──────────────────────────────────────────────────────────────

    override fun observeSources(trackerId: String, type: TransactionType?): Flow<List<TransactionSource>> =
        sourceDao.observeByTracker(trackerId, type).map { rows -> rows.map { it.toDomain() } }

    override suspend fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ): Result<String> = mutate {
        val trimmed = name.trim()
        require(trackerId.isNotBlank()) { "Invalid tracker" }
        require(trimmed.isNotBlank()) { "Source name cannot be empty" }
        requireNotNull(trackerDao.getById(trackerId)?.takeUnless { it.deleted }) {
            FirebaseConst.TRACK_NOT_FOUND
        }

        val stamp = now()
        val id = UUID.randomUUID().toString()
        sourceDao.upsert(
            SourceEntity(
                id = id,
                trackerId = trackerId,
                name = trimmed,
                type = type,
                createdAt = stamp,
                updatedAt = stamp,
                pendingSync = true
            )
        )
        id
    }

    override suspend fun updateSource(
        sourceId: String,
        name: String,
        type: TransactionType
    ): Result<Unit> = mutate {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Source name cannot be empty" }
        val existing = sourceDao.getById(sourceId)?.takeUnless { it.deleted }
            ?: throw IllegalStateException(FirebaseConst.SOURCE_NOT_FOUND)

        sourceDao.upsert(existing.copy(name = trimmed, type = type, updatedAt = now(), pendingSync = true))
    }

    override suspend fun deleteSource(trackerId: String, sourceId: String): Result<Unit> = mutate {
        val existing = sourceDao.getById(sourceId)
            ?: throw IllegalStateException(FirebaseConst.SOURCE_NOT_FOUND)
        val stamp = now()

        database.withTransaction {
            receiptDao.softDeleteBySource(sourceId, stamp)
            sourceDao.upsert(existing.copy(deleted = true, updatedAt = stamp, pendingSync = true))
        }
        clearConflict(ConflictEntityType.SOURCE, sourceId)
    }

    // ── Receipts ─────────────────────────────────────────────────────────────

    override fun observeReceipts(trackerId: String, sourceId: String?): Flow<List<TransactionReceipt>> =
        receiptDao.observeByTracker(trackerId, sourceId?.takeIf { it.isNotBlank() })
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Result<String> = mutate {
        val trimmed = name.trim()
        require(trackerId.isNotBlank() && sourceId.isNotBlank()) { "Invalid tracker or source" }
        require(trimmed.isNotBlank()) { "Receipt name cannot be empty" }
        require(amount.isFinite()) { "Amount is not a valid number" }
        require(amount > 0.0) { "Amount must be greater than zero" }
        requireNotNull(sourceDao.getById(sourceId)?.takeUnless { it.deleted }) {
            FirebaseConst.SOURCE_NOT_FOUND
        }

        val stamp = now()
        val id = UUID.randomUUID().toString()
        receiptDao.upsert(
            ReceiptEntity(
                id = id,
                trackerId = trackerId,
                sourceId = sourceId,
                type = type,
                name = trimmed,
                description = description.trim(),
                amount = amount.roundToCents(),
                date = if (date > 0L) date else stamp,
                createdAt = stamp,
                updatedAt = stamp,
                pendingSync = true
            )
        )
        id
    }

    override suspend fun updateReceipt(receipt: TransactionReceipt): Result<Unit> = mutate {
        val trimmed = receipt.name.trim()
        require(receipt.id.isNotBlank()) { "Invalid receipt" }
        require(trimmed.isNotBlank()) { "Receipt name cannot be empty" }
        require(receipt.amount.isFinite()) { "Amount is not a valid number" }
        require(receipt.amount > 0.0) { "Amount must be greater than zero" }

        val existing = receiptDao.getById(receipt.id)?.takeUnless { it.deleted }
            ?: throw IllegalStateException(FirebaseConst.RECEIPT_NOT_FOUND)
        requireNotNull(sourceDao.getById(receipt.sourceId)?.takeUnless { it.deleted }) {
            FirebaseConst.SOURCE_NOT_FOUND
        }

        receiptDao.upsert(
            existing.copy(
                sourceId = receipt.sourceId,
                type = receipt.type,
                name = trimmed,
                description = receipt.description.trim(),
                amount = receipt.amount.roundToCents(),
                date = if (receipt.date > 0L) receipt.date else existing.date,
                updatedAt = now(),
                pendingSync = true
            )
        )
    }

    override suspend fun deleteReceipt(receiptId: String): Result<Unit> = mutate {
        val existing = receiptDao.getById(receiptId)
            ?: throw IllegalStateException(FirebaseConst.RECEIPT_NOT_FOUND)
        receiptDao.upsert(existing.copy(deleted = true, updatedAt = now(), pendingSync = true))
        clearConflict(ConflictEntityType.RECEIPT, receiptId)
    }

    // ── Export reads ─────────────────────────────────────────────────────────

    override suspend fun getSourcesForExport(trackerId: String): List<TransactionSource> =
        sourceDao.getActiveByTracker(trackerId).mapNotNull { source ->
            sourceDao.getWithTotal(source.id)?.toDomain()
        }

    override suspend fun getReceiptsForExport(trackerId: String): List<TransactionReceipt> =
        receiptDao.getActiveByTracker(trackerId).map { it.toDomain() }

    override suspend fun wipeLocalData() {
        database.withTransaction {
            receiptDao.deleteAll()
            sourceDao.deleteAll()
            trackerDao.deleteAll()
        }
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────

    /**
     * Wraps a local write: validation failures come back as [Result.failure] rather than
     * exceptions, and a successful write asks the scheduler for a sync. The nudge is
     * best-effort — if it fails, the row is still marked dirty and the periodic pass will
     * collect it.
     */
    private suspend inline fun <T> mutate(crossinline block: suspend () -> T): Result<T> =
        runCatching { block() }.onSuccess {
            runCatching { onLocalChange() }
        }

    /**
     * Money is stored as [Double] here. Rounding at the write boundary keeps the aggregate
     * queries from accumulating representation error across many rows — the sum of values that
     * are each exact to the cent stays within a cent of the truth.
     */
    private fun Double.roundToCents(): Double = Math.round(this * 100.0) / 100.0
}
