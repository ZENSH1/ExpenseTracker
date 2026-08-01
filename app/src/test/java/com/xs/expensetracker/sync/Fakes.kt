package com.xs.expensetracker.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.xs.expensetracker.data.local.dao.ReceiptDao
import com.xs.expensetracker.data.local.dao.SourceDao
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.SyncDao
import com.xs.expensetracker.data.local.dao.TrackerDao
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.SourceWithTotal
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.local.entity.TrackerEntity
import com.xs.expensetracker.data.local.entity.TrackerWithTotals
import com.xs.expensetracker.data.remote.RemoteExpenseDataSource
import com.xs.expensetracker.data.remote.RemotePush
import com.xs.expensetracker.data.remote.RemoteReceipt
import com.xs.expensetracker.data.remote.RemoteSnapshot
import com.xs.expensetracker.data.remote.RemoteSource
import com.xs.expensetracker.data.remote.RemoteTracker
import com.xs.expensetracker.data.sync.SyncIdentity
import com.xs.expensetracker.data.sync.SyncLogger
import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory doubles for the sync engine's collaborators.
 *
 * Hand-written rather than mocked: the DAO contracts include behaviour that matters to the
 * tests (compare-and-clear on `markSynced`, tombstone filtering) and a mock would let those
 * details drift silently out of step with the real SQL.
 */

// ── DataStore ────────────────────────────────────────────────────────────────

class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

// ── Identity ─────────────────────────────────────────────────────────────────

class FakeIdentity(
    var uid: String? = null,
    private val localId: String = "local:device-1"
) : SyncIdentity {
    override fun currentUid(): String? = uid
    override suspend fun currentOwnerId(): String = uid ?: localId
}

// ── Logger ───────────────────────────────────────────────────────────────────

class RecordingLogger : SyncLogger {
    val events = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()

    override fun debug(tag: String, message: String) = Unit

    override fun error(tag: String, operation: String, throwable: Throwable, extra: Map<String, String>) {
        errors += throwable
    }

    override fun event(name: String, params: Map<String, String>) {
        events += name
    }
}

// ── DAOs ─────────────────────────────────────────────────────────────────────

class FakeTrackerDao : TrackerDao {
    val rows = linkedMapOf<String, TrackerEntity>()

    override fun observeAll(): Flow<List<TrackerWithTotals>> =
        MutableStateFlow(Unit).map { emptyList() }

    override fun observeById(trackerId: String): Flow<TrackerWithTotals?> =
        MutableStateFlow(Unit).map { null }

    override suspend fun getById(trackerId: String) = rows[trackerId]
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getPending() = rows.values.filter { it.pendingSync }.sortedBy { it.updatedAt }
    override suspend fun countActive() = rows.values.count { !it.deleted }

    override suspend fun upsert(tracker: TrackerEntity) {
        rows[tracker.id] = tracker
    }

    override suspend fun upsertAll(trackers: List<TrackerEntity>) = trackers.forEach { upsert(it) }

    /** Mirrors the production `WHERE id = :id AND updatedAt = :pushedUpdatedAt` guard. */
    override suspend fun markSynced(trackerId: String, pushedUpdatedAt: Long): Int {
        val row = rows[trackerId] ?: return 0
        if (row.updatedAt != pushedUpdatedAt) return 0
        rows[trackerId] = row.copy(pendingSync = false, remoteUpdatedAt = pushedUpdatedAt)
        return 1
    }

    override suspend fun markAllPending(now: Long) {
        rows.values.filterNot { it.deleted }.forEach {
            rows[it.id] = it.copy(pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun purgeTombstones(before: Long): Int {
        val doomed = rows.values.filter { it.deleted && !it.pendingSync && it.updatedAt < before }
        doomed.forEach { rows.remove(it.id) }
        return doomed.size
    }

    override suspend fun deleteById(trackerId: String) {
        rows.remove(trackerId)
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeSourceDao : SourceDao {
    val rows = linkedMapOf<String, SourceEntity>()
    var receiptsProvider: () -> List<ReceiptEntity> = { emptyList() }
    var trackerIdsProvider: () -> Set<String> = { emptySet() }

    override fun observeByTracker(trackerId: String, type: TransactionType?): Flow<List<SourceWithTotal>> =
        MutableStateFlow(Unit).map { emptyList() }

    override suspend fun getWithTotal(sourceId: String): SourceWithTotal? {
        val source = rows[sourceId]?.takeUnless { it.deleted } ?: return null
        val total = receiptsProvider().filter { it.sourceId == sourceId && !it.deleted }.sumOf { it.amount }
        return SourceWithTotal(source, total)
    }

    override suspend fun getById(sourceId: String) = rows[sourceId]
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getActiveByTracker(trackerId: String) =
        rows.values.filter { it.trackerId == trackerId && !it.deleted }

    override suspend fun getPending() = rows.values.filter { it.pendingSync }.sortedBy { it.updatedAt }

    override suspend fun upsert(source: SourceEntity) {
        rows[source.id] = source
    }

    override suspend fun upsertAll(sources: List<SourceEntity>) = sources.forEach { upsert(it) }

    override suspend fun markSynced(sourceId: String, pushedUpdatedAt: Long): Int {
        val row = rows[sourceId] ?: return 0
        if (row.updatedAt != pushedUpdatedAt) return 0
        rows[sourceId] = row.copy(pendingSync = false, remoteUpdatedAt = pushedUpdatedAt)
        return 1
    }

    override suspend fun softDeleteByTracker(trackerId: String, now: Long) {
        rows.values.filter { it.trackerId == trackerId && !it.deleted }.forEach {
            rows[it.id] = it.copy(deleted = true, pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun markAllPending(now: Long) {
        rows.values.filterNot { it.deleted }.forEach {
            rows[it.id] = it.copy(pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun deleteOrphans(): Int {
        val known = trackerIdsProvider()
        val doomed = rows.values.filter { it.trackerId !in known }
        doomed.forEach { rows.remove(it.id) }
        return doomed.size
    }

    override suspend fun purgeTombstones(before: Long): Int {
        val doomed = rows.values.filter { it.deleted && !it.pendingSync && it.updatedAt < before }
        doomed.forEach { rows.remove(it.id) }
        return doomed.size
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeReceiptDao : ReceiptDao {
    val rows = linkedMapOf<String, ReceiptEntity>()
    var trackerIdsProvider: () -> Set<String> = { emptySet() }

    override fun observeByTracker(trackerId: String, sourceId: String?): Flow<List<ReceiptEntity>> =
        MutableStateFlow(Unit).map { emptyList() }

    override suspend fun getActiveByTracker(trackerId: String) =
        rows.values.filter { it.trackerId == trackerId && !it.deleted }.sortedByDescending { it.date }

    override suspend fun getById(receiptId: String) = rows[receiptId]
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getPending() = rows.values.filter { it.pendingSync }.sortedBy { it.updatedAt }

    override suspend fun upsert(receipt: ReceiptEntity) {
        rows[receipt.id] = receipt
    }

    override suspend fun upsertAll(receipts: List<ReceiptEntity>) = receipts.forEach { upsert(it) }

    override suspend fun markSynced(receiptId: String, pushedUpdatedAt: Long): Int {
        val row = rows[receiptId] ?: return 0
        if (row.updatedAt != pushedUpdatedAt) return 0
        rows[receiptId] = row.copy(pendingSync = false, remoteUpdatedAt = pushedUpdatedAt)
        return 1
    }

    override suspend fun softDeleteByTracker(trackerId: String, now: Long) {
        rows.values.filter { it.trackerId == trackerId && !it.deleted }.forEach {
            rows[it.id] = it.copy(deleted = true, pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun softDeleteBySource(sourceId: String, now: Long) {
        rows.values.filter { it.sourceId == sourceId && !it.deleted }.forEach {
            rows[it.id] = it.copy(deleted = true, pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun markAllPending(now: Long) {
        rows.values.filterNot { it.deleted }.forEach {
            rows[it.id] = it.copy(pendingSync = true, updatedAt = now)
        }
    }

    override suspend fun deleteOrphans(): Int {
        val known = trackerIdsProvider()
        val doomed = rows.values.filter { it.trackerId !in known }
        doomed.forEach { rows.remove(it.id) }
        return doomed.size
    }

    override suspend fun purgeTombstones(before: Long): Int {
        val doomed = rows.values.filter { it.deleted && !it.pendingSync && it.updatedAt < before }
        doomed.forEach { rows.remove(it.id) }
        return doomed.size
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeConflictDao : SyncConflictDao {
    val rows = linkedMapOf<String, SyncConflictEntity>()

    override fun observeAll(): Flow<List<SyncConflictEntity>> = MutableStateFlow(rows.values.toList())
    override fun observeCount(): Flow<Int> = MutableStateFlow(rows.size)
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getById(id: String) = rows[id]

    override suspend fun conflictedIdsOfType(entityType: String) =
        rows.values.filter { it.entityType.name == entityType }.map { it.entityId }

    override suspend fun upsert(conflict: SyncConflictEntity) {
        rows[conflict.id] = conflict
    }

    override suspend fun upsertAll(conflicts: List<SyncConflictEntity>) = conflicts.forEach { upsert(it) }

    override suspend fun deleteById(id: String) {
        rows.remove(id)
    }

    override suspend fun deleteAll() = rows.clear()
}

class FakeSyncDao(
    private val trackers: FakeTrackerDao,
    private val sources: FakeSourceDao,
    private val receipts: FakeReceiptDao
) : SyncDao {
    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

    override fun observeActiveRecordCount(): Flow<Int> = MutableStateFlow(0)

    override suspend fun countActiveRecords(): Int =
        trackers.rows.values.count { !it.deleted } +
            sources.rows.values.count { !it.deleted } +
            receipts.rows.values.count { !it.deleted }
}

// ── Remote ───────────────────────────────────────────────────────────────────

class FakeRemote : RemoteExpenseDataSource {
    val trackers = linkedMapOf<String, RemoteTracker>()
    val sources = linkedMapOf<String, RemoteSource>()
    val receipts = linkedMapOf<String, RemoteReceipt>()

    /** Receipts stored under the pre-offline `sources/{id}/receipts` path. */
    val legacyReceipts = mutableListOf<RemoteReceipt>()

    val pushes = mutableListOf<RemotePush>()
    var failWith: Throwable? = null

    /** Fails only writes, leaving reads working — the shape of a rules rejection. */
    var failPushWith: Throwable? = null
    var deletedTrees = mutableListOf<String>()

    /** Hook for simulating a local edit landing while the push is in flight. */
    var onPush: (suspend () -> Unit)? = null

    override suspend fun fetchTrackers(uid: String): List<RemoteTracker> {
        failWith?.let { throw it }
        return trackers.values.filter { uid in it.sharedWith }
    }

    override suspend fun fetchChanges(
        uid: String,
        trackerIds: List<String>,
        since: Long
    ): RemoteSnapshot {
        failWith?.let { throw it }
        return RemoteSnapshot(
            sources = sources.values.filter { it.trackerId in trackerIds && it.updatedAt >= since },
            receipts = receipts.values.filter { it.trackerId in trackerIds && it.updatedAt >= since }
        )
    }

    override suspend fun fetchLegacyReceipts(trackerId: String, sourceIds: List<String>) =
        legacyReceipts.filter { it.trackerId == trackerId && it.sourceId in sourceIds }

    override suspend fun push(push: RemotePush) {
        failWith?.let { throw it }
        failPushWith?.let { throw it }
        pushes += push
        push.trackers.forEach { trackers[it.id] = it }
        push.sources.forEach { sources[it.id] = it }
        push.receipts.forEach { receipts[it.id] = it }
        onPush?.invoke()
    }

    override suspend fun deleteTrackerTree(trackerId: String) {
        deletedTrees += trackerId
        trackers.remove(trackerId)
        sources.values.filter { it.trackerId == trackerId }.forEach { sources.remove(it.id) }
        receipts.values.filter { it.trackerId == trackerId }.forEach { receipts.remove(it.id) }
    }

    override suspend fun purgeTombstones(trackerId: String, before: Long) = Unit

    /** All records written across every push, flattened for assertions. */
    fun pushedTrackerIds() = pushes.flatMap { it.trackers }.map { it.id }
    fun pushedReceiptIds() = pushes.flatMap { it.receipts }.map { it.id }
}
