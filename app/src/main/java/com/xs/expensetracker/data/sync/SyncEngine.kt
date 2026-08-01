package com.xs.expensetracker.data.sync

import com.google.firebase.firestore.FirebaseFirestoreException
import com.xs.expensetracker.data.local.dao.ReceiptDao
import com.xs.expensetracker.data.local.dao.SourceDao
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.SyncDao
import com.xs.expensetracker.data.local.dao.TrackerDao
import com.xs.expensetracker.data.local.entity.ConflictEntityType
import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceEntity
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.local.entity.TrackerEntity
import com.xs.expensetracker.data.prefs.SyncPreferences
import com.xs.expensetracker.data.remote.RemoteExpenseDataSource
import com.xs.expensetracker.data.remote.RemotePush
import com.xs.expensetracker.data.remote.RemoteReceipt
import com.xs.expensetracker.data.remote.RemoteSnapshot
import com.xs.expensetracker.data.remote.RemoteSource
import com.xs.expensetracker.data.remote.RemoteTracker
import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.UnknownHostException
import kotlin.math.abs

/**
 * Reconciles the local database with the cloud copy.
 *
 * ### Shape of a run
 *
 * Pull first, then push. Pulling first is what makes conflict detection possible: a local row
 * that is dirty *and* whose remote counterpart has moved since the baseline we recorded is a
 * genuine two-sided edit, and it is parked in the conflicts table instead of being pushed.
 * Everything else pushes cleanly.
 *
 * ### Why baselines instead of timestamps
 *
 * Each row remembers `remoteUpdatedAt` — the remote revision it was last reconciled against.
 * A conflict is "remote moved away from my baseline while I also had local edits", which is a
 * three-way comparison and therefore immune to device clock skew. Comparing local and remote
 * timestamps directly would hand the win to whichever device has the faster clock.
 *
 * ### What is never done silently
 *
 * - Overwriting local edits with cloud edits, or the reverse.
 * - Uploading one account's records into another account's cloud.
 * - Hard-deleting anything that has not yet propagated.
 */
class SyncEngine(
    private val trackerDao: TrackerDao,
    private val sourceDao: SourceDao,
    private val receiptDao: ReceiptDao,
    private val conflictDao: SyncConflictDao,
    private val syncDao: SyncDao,
    private val remote: RemoteExpenseDataSource,
    private val identity: SyncIdentity,
    private val preferences: SyncPreferences,
    private val logger: SyncLogger,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** One run at a time. A manual "Sync now" landing on top of the periodic run must queue. */
    private val mutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param manual `true` for a user-initiated sync. Manual runs ignore the auto-sync toggle
     *   and pull the full history rather than an incremental slice, which is the escape hatch
     *   when a peer's clock skew has pushed a record under the watermark.
     */
    suspend fun sync(mode: SyncMode = SyncMode.NORMAL, manual: Boolean = false): SyncOutcome =
        mutex.withLock {
            val outcome = try {
                runSync(mode, manual)
            } catch (e: Throwable) {
                classify(e)
            }
            recordOutcome(outcome)
            outcome
        }

    private suspend fun runSync(requestedMode: SyncMode, manual: Boolean): SyncOutcome {
        val uid = identity.currentUid()
            ?: return SyncOutcome.Blocked(SyncBlockReason.NOT_SIGNED_IN)

        if (!manual && !preferences.autoSyncEnabledOnce()) {
            return SyncOutcome.Blocked(SyncBlockReason.AUTO_SYNC_DISABLED)
        }

        checkAccountChange(uid)?.let { return it }

        val mode = if (requestedMode == SyncMode.NORMAL) {
            resolveFirstSyncMode(uid) ?: run {
                val localCount = syncDao.countActiveRecords()
                val remoteCount = remote.fetchTrackers(uid).count { !it.deleted }
                // Recorded durably: this decision is raised inside a background worker, and the
                // user may not open the app until much later.
                preferences.setPendingLink(uid, localCount, remoteCount)
                return SyncOutcome.Blocked(
                    reason = SyncBlockReason.NEEDS_LINK_DECISION,
                    localRecordCount = localCount,
                    remoteTrackerCount = remoteCount
                )
            }
        } else {
            requestedMode
        }

        logger.debug(TAG, "sync start uid=$uid mode=$mode manual=$manual")

        return when (mode) {
            SyncMode.FORCE_DOWNLOAD -> importFromCloud(uid)
            SyncMode.FORCE_UPLOAD -> exportToCloud(uid)
            SyncMode.MERGE -> mergeWithCloud(uid)
            SyncMode.NORMAL -> incrementalSync(uid, fullPull = manual)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Guards
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A different account signing in on a device that still holds the previous account's data
     * is the one case where proceeding could leak data between users, so it always stops and
     * asks. If the device holds nothing, the switch is silent — there is nothing to decide.
     */
    private suspend fun checkAccountChange(uid: String): SyncOutcome? {
        val previousUid = preferences.lastSyncedUid()
        val awaiting = preferences.pendingAccountUid()

        if (awaiting != null && awaiting == uid) {
            return SyncOutcome.Blocked(
                reason = SyncBlockReason.NEEDS_ACCOUNT_DECISION,
                previousUid = previousUid,
                newUid = uid,
                localRecordCount = syncDao.countActiveRecords()
            )
        }

        if (previousUid == null || previousUid == uid) return null

        if (syncDao.countActiveRecords() == 0) {
            // Nothing local to reconcile — adopt the new account outright. The watermark must
            // go with it, or the first pull would skip everything older than the old account's
            // last sync.
            preferences.resetSyncState()
            return null
        }

        preferences.setPendingAccountUid(uid)
        return SyncOutcome.Blocked(
            reason = SyncBlockReason.NEEDS_ACCOUNT_DECISION,
            previousUid = previousUid,
            newUid = uid,
            localRecordCount = syncDao.countActiveRecords()
        )
    }

    /**
     * Decides how the very first sync for an account should behave, or returns `null` when
     * both sides hold data and only the user can decide.
     */
    private suspend fun resolveFirstSyncMode(uid: String): SyncMode? {
        if (preferences.isLinked(uid)) return SyncMode.NORMAL

        val localCount = syncDao.countActiveRecords()
        val remoteTrackers = remote.fetchTrackers(uid).filterNot { it.deleted }

        return when {
            localCount > 0 && remoteTrackers.isNotEmpty() -> null          // ask the user
            localCount > 0 -> SyncMode.FORCE_UPLOAD                        // claim local data
            remoteTrackers.isNotEmpty() -> SyncMode.FORCE_DOWNLOAD         // adopt cloud data
            else -> SyncMode.NORMAL                                        // both empty
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync strategies
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun incrementalSync(uid: String, fullPull: Boolean): SyncOutcome {
        val since = if (fullPull) 0L else preferences.lastPullAt()
        val pullStartedAt = now()

        val remoteTrackers = remote.fetchTrackers(uid)
        val trackerIds = remoteTrackers.map { it.id }
        val knownLocally = trackerDao.getAll().map { it.id }.toSet()

        // A tracker is reconciled if it changed since the watermark *or* this device has never
        // seen it. Without that second clause, a tracker whose own document happens to sit
        // below the watermark would never be inserted — and its sources and receipts would
        // then arrive as parentless rows and be swept away as orphans.
        val changes = remote.fetchChanges(uid, trackerIds, since).let { snapshot ->
            snapshot.copy(
                trackers = remoteTrackers.filter { t ->
                    since <= 0L || t.updatedAt >= since || t.id !in knownLocally
                }
            )
        }

        val reconciled = reconcile(changes, SyncMode.NORMAL)
        val pushed = pushPending(uid)

        preferences.setLastPullAt(pullStartedAt)
        markAccountLinked(uid)
        purgeOldTombstones(uid, remoteTrackers)

        return SyncOutcome.Success(
            pushed = pushed,
            pulled = reconciled.applied,
            conflicts = reconciled.conflicts
        )
    }

    /** "Import from cloud": the cloud copy replaces everything on this device. */
    private suspend fun importFromCloud(uid: String): SyncOutcome {
        val pullStartedAt = now()
        val snapshot = fetchEverything(uid)

        // Wipe rather than reconcile — the user asked for a replacement, and leaving stray local
        // rows behind would quietly turn "import" into "merge".
        receiptDao.deleteAll()
        sourceDao.deleteAll()
        trackerDao.deleteAll()
        conflictDao.deleteAll()

        trackerDao.upsertAll(snapshot.trackers.filterNot { it.deleted }.map { it.toCleanEntity() })
        sourceDao.upsertAll(snapshot.sources.filterNot { it.deleted }.map { it.toCleanEntity() })
        receiptDao.upsertAll(
            snapshot.receipts.filterNot { it.deleted }
                .map { it.toCleanEntity(resolveTypeFor(it, snapshot.sources)) }
        )
        dropOrphans()

        preferences.setLastPullAt(pullStartedAt)
        markAccountLinked(uid)

        val pulled = snapshot.trackers.size + snapshot.sources.size + snapshot.receipts.size
        logger.event("sync_import_from_cloud", mapOf("records" to pulled.toString()))
        return SyncOutcome.Success(pulled = pulled)
    }

    /** "Export to cloud": the cloud is rewritten to match this device. */
    private suspend fun exportToCloud(uid: String): SyncOutcome {
        val pullStartedAt = now()
        claimLocalRecords(uid)

        // Read the cloud only to find records that no longer exist here; they need tombstones
        // or the next import would resurrect them.
        val snapshot = fetchEverything(uid)
        val localTrackerIds = trackerDao.getAll().filterNot { it.deleted }.map { it.id }.toSet()
        val localSourceIds = sourceDao.getAll().filterNot { it.deleted }.map { it.id }.toSet()
        val localReceiptIds = receiptDao.getAll().filterNot { it.deleted }.map { it.id }.toSet()

        // Scoped to trackers this account owns. "Export to cloud" rewrites *your* cloud copy;
        // it must not reach into a tracker someone else shared with you and start deleting
        // records out of it.
        val ownedTrackerIds = snapshot.trackers.filter { it.ownerId == uid }.map { it.id }.toSet()

        val stamp = now()
        val extraTombstones = RemotePush(
            trackers = snapshot.trackers
                .filter { !it.deleted && it.id !in localTrackerIds && it.ownerId == uid }
                .map { it.copy(deleted = true, updatedAt = stamp) },
            sources = snapshot.sources
                .filter { !it.deleted && it.id !in localSourceIds && it.trackerId in ownedTrackerIds }
                .map { it.copy(deleted = true, updatedAt = stamp) },
            receipts = snapshot.receipts
                .filter { !it.deleted && it.id !in localReceiptIds && it.trackerId in ownedTrackerIds }
                .map { it.copy(deleted = true, updatedAt = stamp) }
        )

        trackerDao.markAllPending(stamp)
        sourceDao.markAllPending(stamp)
        receiptDao.markAllPending(stamp)
        conflictDao.deleteAll()

        val pushed = pushPending(uid, additional = extraTombstones)

        preferences.setLastPullAt(pullStartedAt)
        markAccountLinked(uid)

        logger.event("sync_export_to_cloud", mapOf("records" to pushed.toString()))
        return SyncOutcome.Success(pushed = pushed)
    }

    /** Union of both sides; where a record exists in both, the newer revision wins. */
    private suspend fun mergeWithCloud(uid: String): SyncOutcome {
        val pullStartedAt = now()
        val snapshot = fetchEverything(uid)

        val reconciled = reconcile(snapshot, SyncMode.MERGE)
        claimLocalRecords(uid)

        val stamp = now()
        trackerDao.markAllPending(stamp)
        sourceDao.markAllPending(stamp)
        receiptDao.markAllPending(stamp)
        conflictDao.deleteAll()

        val pushed = pushPending(uid)

        preferences.setLastPullAt(pullStartedAt)
        markAccountLinked(uid)

        logger.event("sync_merge", mapOf("pulled" to reconciled.applied.toString(), "pushed" to pushed.toString()))
        return SyncOutcome.Success(pushed = pushed, pulled = reconciled.applied)
    }

    /** Full remote read, including receipts left behind by the pre-offline document layout. */
    private suspend fun fetchEverything(uid: String): RemoteSnapshot {
        val trackers = remote.fetchTrackers(uid)
        val ids = trackers.map { it.id }
        val changes = remote.fetchChanges(uid, ids, since = 0L)

        val knownReceiptIds = changes.receipts.map { it.id }.toSet()
        val legacy = trackers.flatMap { tracker ->
            val sourceIds = changes.sources.filter { it.trackerId == tracker.id }.map { it.id }
            if (sourceIds.isEmpty()) emptyList()
            else remote.fetchLegacyReceipts(tracker.id, sourceIds).filter { it.id !in knownReceiptIds }
        }

        return changes.copy(trackers = trackers, receipts = changes.receipts + legacy)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reconcile (pull side)
    // ─────────────────────────────────────────────────────────────────────────

    private data class ReconcileResult(val applied: Int = 0, val conflicts: Int = 0)

    private suspend fun reconcile(snapshot: RemoteSnapshot, mode: SyncMode): ReconcileResult {
        var applied = 0
        var conflicts = 0
        val newConflicts = mutableListOf<SyncConflictEntity>()

        // Trackers ────────────────────────────────────────────────────────────
        val trackerUpdates = mutableListOf<TrackerEntity>()
        for (r in snapshot.trackers) {
            when (decide(trackerDao.getById(r.id), r.updatedAt, mode)) {
                Decision.TAKE_REMOTE -> { trackerUpdates += r.toCleanEntity(); applied++ }
                Decision.CONFLICT -> {
                    val local = trackerDao.getById(r.id)!!
                    newConflicts += conflictOf(
                        type = ConflictEntityType.TRACKER,
                        entityId = r.id,
                        trackerId = r.id,
                        label = local.name.ifBlank { r.name },
                        localSummary = summaryOf(local),
                        remoteSummary = summaryOf(r),
                        localDeleted = local.deleted,
                        remoteDeleted = r.deleted,
                        payload = json.encodeToString(RemoteTracker.serializer(), r)
                    )
                    conflicts++
                }
                else -> Unit
            }
        }
        if (trackerUpdates.isNotEmpty()) trackerDao.upsertAll(trackerUpdates)

        // Sources ─────────────────────────────────────────────────────────────
        val sourceUpdates = mutableListOf<SourceEntity>()
        for (r in snapshot.sources) {
            when (decide(sourceDao.getById(r.id), r.updatedAt, mode)) {
                Decision.TAKE_REMOTE -> { sourceUpdates += r.toCleanEntity(); applied++ }
                Decision.CONFLICT -> {
                    val local = sourceDao.getById(r.id)!!
                    newConflicts += conflictOf(
                        type = ConflictEntityType.SOURCE,
                        entityId = r.id,
                        trackerId = r.trackerId,
                        label = local.name.ifBlank { r.name },
                        localSummary = summaryOf(local),
                        remoteSummary = summaryOf(r),
                        localDeleted = local.deleted,
                        remoteDeleted = r.deleted,
                        payload = json.encodeToString(RemoteSource.serializer(), r)
                    )
                    conflicts++
                }
                else -> Unit
            }
        }
        if (sourceUpdates.isNotEmpty()) sourceDao.upsertAll(sourceUpdates)

        // Receipts ────────────────────────────────────────────────────────────
        val receiptUpdates = mutableListOf<ReceiptEntity>()
        for (r in snapshot.receipts) {
            when (decide(receiptDao.getById(r.id), r.updatedAt, mode)) {
                Decision.TAKE_REMOTE -> {
                    receiptUpdates += r.toCleanEntity(resolveTypeFor(r, snapshot.sources))
                    applied++
                }
                Decision.CONFLICT -> {
                    val local = receiptDao.getById(r.id)!!
                    newConflicts += conflictOf(
                        type = ConflictEntityType.RECEIPT,
                        entityId = r.id,
                        trackerId = r.trackerId,
                        label = local.name.ifBlank { r.name },
                        localSummary = summaryOf(local),
                        remoteSummary = summaryOf(r),
                        localDeleted = local.deleted,
                        remoteDeleted = r.deleted,
                        payload = json.encodeToString(RemoteReceipt.serializer(), r)
                    )
                    conflicts++
                }
                else -> Unit
            }
        }
        if (receiptUpdates.isNotEmpty()) receiptDao.upsertAll(receiptUpdates)

        if (newConflicts.isNotEmpty()) conflictDao.upsertAll(newConflicts)
        dropOrphans()

        return ReconcileResult(applied, conflicts)
    }

    private enum class Decision { TAKE_REMOTE, KEEP_LOCAL, CONFLICT }

    /**
     * The whole of the reconcile policy, in one place.
     *
     * [localPending] carrying local edits is what separates "the cloud is simply ahead" from
     * "both sides moved". The baseline check below (`remoteUpdatedAt == remote.updatedAt`)
     * catches the common false alarm where our own last push is echoed back to us.
     */
    private fun decide(local: SyncRow?, remoteUpdatedAt: Long, mode: SyncMode): Decision = when {
        local == null -> Decision.TAKE_REMOTE
        !local.pendingSync -> Decision.TAKE_REMOTE
        local.remoteUpdatedAt == remoteUpdatedAt -> Decision.KEEP_LOCAL
        mode == SyncMode.FORCE_DOWNLOAD -> Decision.TAKE_REMOTE
        mode == SyncMode.FORCE_UPLOAD -> Decision.KEEP_LOCAL
        mode == SyncMode.MERGE ->
            if (remoteUpdatedAt > local.updatedAt) Decision.TAKE_REMOTE else Decision.KEEP_LOCAL
        else -> Decision.CONFLICT
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Push side
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pushes every dirty row except those sitting in the conflicts table, then clears their
     * dirty flag — but only for rows the user has not edited again in the meantime.
     */
    private suspend fun pushPending(uid: String, additional: RemotePush = RemotePush()): Int {
        val conflictedTrackers = conflictDao.conflictedIdsOfType(ConflictEntityType.TRACKER.name).toSet()
        val conflictedSources = conflictDao.conflictedIdsOfType(ConflictEntityType.SOURCE.name).toSet()
        val conflictedReceipts = conflictDao.conflictedIdsOfType(ConflictEntityType.RECEIPT.name).toSet()

        val trackers = trackerDao.getPending()
            .filter { it.id !in conflictedTrackers && it.canBeWrittenBy(uid) }
        val sources = sourceDao.getPending().filter { it.id !in conflictedSources }
        val receipts = receiptDao.getPending().filter { it.id !in conflictedReceipts }

        // Children of a tracker this account may not write would be rejected by the security
        // rules; drop them from the batch rather than failing the whole push.
        val writableTrackerIds = trackerDao.getAll().filter { it.canBeWrittenBy(uid) }.map { it.id }.toSet()
        val pushableSources = sources.filter { it.trackerId in writableTrackerIds }
        val pushableReceipts = receipts.filter { it.trackerId in writableTrackerIds }

        val push = RemotePush(
            trackers = trackers.map { it.toRemote(grandTotal = grandTotalOf(it.id)) } + additional.trackers,
            sources = pushableSources.map { it.toRemote(totalAmount = totalOf(it.id)) } + additional.sources,
            receipts = pushableReceipts.map { it.toRemote() } + additional.receipts
        )

        if (push.isEmpty) return 0

        remote.push(push)

        // Compare-and-clear: a row edited while the network call was in flight has a different
        // `updatedAt`, matches nothing, and stays queued for the next run.
        trackers.forEach { trackerDao.markSynced(it.id, it.updatedAt) }
        pushableSources.forEach { sourceDao.markSynced(it.id, it.updatedAt) }
        pushableReceipts.forEach { receiptDao.markSynced(it.id, it.updatedAt) }

        return trackers.size + pushableSources.size + pushableReceipts.size
    }

    private fun TrackerEntity.canBeWrittenBy(uid: String): Boolean =
        ownerId == uid || uid in sharedWith || SyncPreferences.isLocalId(ownerId)

    private suspend fun grandTotalOf(trackerId: String): Double =
        receiptDao.getActiveByTracker(trackerId).sumOf {
            if (it.type == TransactionType.INCOME) it.amount else -it.amount
        }

    private suspend fun totalOf(sourceId: String): Double =
        sourceDao.getWithTotal(sourceId)?.totalAmount ?: 0.0

    // ─────────────────────────────────────────────────────────────────────────
    // Claiming and ownership
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rewrites device-local ownership to the signed-in account.
     *
     * Only trackers owned by a `local:` id are touched. A tracker someone else shared with this
     * user carries their uid and must keep it — claiming it would try to steal the document and
     * be rejected by the security rules anyway.
     */
    private suspend fun claimLocalRecords(uid: String) {
        val claimable = trackerDao.getAll().filter { SyncPreferences.isLocalId(it.ownerId) }
        if (claimable.isEmpty()) return

        val stamp = now()
        trackerDao.upsertAll(
            claimable.map { tracker ->
                tracker.copy(
                    ownerId = uid,
                    sharedWith = (tracker.sharedWith.filterNot { SyncPreferences.isLocalId(it) } + uid).distinct(),
                    updatedAt = stamp,
                    pendingSync = true
                )
            }
        )
        logger.event("sync_claimed_local_data", mapOf("trackers" to claimable.size.toString()))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User decisions
    // ─────────────────────────────────────────────────────────────────────────

    /** Applies the user's answer to the first-sync prompt and runs that sync immediately. */
    suspend fun resolveLinkDecision(mode: SyncMode): SyncOutcome = sync(mode = mode, manual = true)

    /**
     * Answers the account-switch prompt.
     *
     * @param keepLocalData `true` moves this device's records into the newly signed-in account;
     *   `false` discards them and adopts the new account's cloud data. Trackers belonging to a
     *   third party are dropped either way — they were readable through the previous account's
     *   grants, not this one's.
     */
    suspend fun resolveAccountChange(keepLocalData: Boolean): SyncOutcome {
        val uid = identity.currentUid()
            ?: return SyncOutcome.Blocked(SyncBlockReason.NOT_SIGNED_IN)
        val previousUid = preferences.lastSyncedUid()

        if (keepLocalData) {
            val stamp = now()
            val all = trackerDao.getAll()
            val (mine, theirs) = all.partition {
                SyncPreferences.isLocalId(it.ownerId) || it.ownerId == previousUid || it.ownerId == uid
            }

            trackerDao.upsertAll(
                mine.filter { it.ownerId != uid }.map {
                    it.copy(
                        ownerId = uid,
                        sharedWith = (it.sharedWith.filterNot { s -> SyncPreferences.isLocalId(s) || s == previousUid } + uid).distinct(),
                        updatedAt = stamp,
                        pendingSync = true
                    )
                }
            )

            // Third-party trackers came with the previous account's grants and are not this
            // user's to carry over. Hard-deleted, deliberately without tombstones: a tombstone
            // would be pushed and would delete the real owner's data. Removing the parent lets
            // dropOrphans sweep the children.
            theirs.forEach { trackerDao.deleteById(it.id) }
            dropOrphans()
        } else {
            receiptDao.deleteAll()
            sourceDao.deleteAll()
            trackerDao.deleteAll()
        }

        conflictDao.deleteAll()
        preferences.resetSyncState()
        preferences.setPendingAccountUid(null)

        logger.event("sync_account_switch", mapOf("kept_local" to keepLocalData.toString()))
        return sync(manual = true)
    }

    /**
     * Permanently removes this user's cloud data, for account deletion.
     *
     * Only trackers they own are destroyed. Trackers merely shared *with* them belong to
     * someone else and must survive; the user is dropped from the share list instead.
     */
    suspend fun purgeCloudData(): Result<Unit> = runCatching {
        val uid = identity.currentUid() ?: return@runCatching
        val trackers = remote.fetchTrackers(uid)
        val (owned, shared) = trackers.partition { it.ownerId == uid }

        owned.forEach { remote.deleteTrackerTree(it.id) }

        // Best-effort: the owner's rules may not permit a collaborator to edit the share list.
        // Failing here would block account deletion over a cosmetic leftover, so it is logged
        // and swallowed rather than propagated.
        if (shared.isNotEmpty()) {
            runCatching {
                remote.push(
                    RemotePush(
                        trackers = shared.map {
                            it.copy(sharedWith = it.sharedWith - uid, updatedAt = now())
                        }
                    )
                )
            }.onFailure { logger.debug(TAG, "could not leave shared trackers: ${it.message}") }
        }
        logger.event("cloud_data_purged", mapOf("owned" to owned.size.toString()))
    }

    /** Applies the user's choice for one conflicted record. */
    suspend fun resolveConflict(conflictId: String, resolution: ConflictResolution) {
        val conflict = conflictDao.getById(conflictId) ?: return
        applyResolution(conflict, resolution)
        conflictDao.deleteById(conflictId)
    }

    /** Applies one choice to every outstanding conflict. */
    suspend fun resolveAllConflicts(resolution: ConflictResolution) {
        conflictDao.getAll().forEach { applyResolution(it, resolution) }
        conflictDao.deleteAll()
    }

    private suspend fun applyResolution(conflict: SyncConflictEntity, resolution: ConflictResolution) {
        when (conflict.entityType) {
            ConflictEntityType.TRACKER -> {
                val r = json.decodeFromString(RemoteTracker.serializer(), conflict.remotePayload)
                when (resolution) {
                    ConflictResolution.KEEP_REMOTE -> trackerDao.upsert(r.toCleanEntity())
                    ConflictResolution.KEEP_LOCAL -> trackerDao.getById(conflict.entityId)?.let {
                        // Adopt the remote revision as the new baseline and stamp the local row
                        // fresh, so it outranks the remote copy on the next push and on peers'
                        // incremental pulls.
                        trackerDao.upsert(it.copy(updatedAt = now(), pendingSync = true, remoteUpdatedAt = r.updatedAt))
                    }
                }
            }

            ConflictEntityType.SOURCE -> {
                val r = json.decodeFromString(RemoteSource.serializer(), conflict.remotePayload)
                when (resolution) {
                    ConflictResolution.KEEP_REMOTE -> sourceDao.upsert(r.toCleanEntity())
                    ConflictResolution.KEEP_LOCAL -> sourceDao.getById(conflict.entityId)?.let {
                        sourceDao.upsert(it.copy(updatedAt = now(), pendingSync = true, remoteUpdatedAt = r.updatedAt))
                    }
                }
            }

            ConflictEntityType.RECEIPT -> {
                val r = json.decodeFromString(RemoteReceipt.serializer(), conflict.remotePayload)
                when (resolution) {
                    ConflictResolution.KEEP_REMOTE -> {
                        val fallback = sourceDao.getById(r.sourceId)?.type ?: TransactionType.EXPENSE
                        receiptDao.upsert(r.toCleanEntity(parseType(r.type) ?: fallback))
                    }
                    ConflictResolution.KEEP_LOCAL -> receiptDao.getById(conflict.entityId)?.let {
                        receiptDao.upsert(it.copy(updatedAt = now(), pendingSync = true, remoteUpdatedAt = r.updatedAt))
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Housekeeping
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tombstones exist only to carry a deletion to other devices. Once they are older than the
     * retention window and fully pushed, they are dead weight.
     */
    private suspend fun purgeOldTombstones(uid: String, remoteTrackers: List<RemoteTracker>) {
        val cutoff = now() - TOMBSTONE_RETENTION_MS
        if (cutoff <= 0) return

        receiptDao.purgeTombstones(cutoff)
        sourceDao.purgeTombstones(cutoff)
        trackerDao.purgeTombstones(cutoff)

        // Only the owner prunes the cloud copy; a shared collaborator doing it would race with
        // devices that have not pulled the deletion yet.
        remoteTrackers.filter { it.ownerId == uid && !it.deleted }.forEach { tracker ->
            runCatching { remote.purgeTombstones(tracker.id, cutoff) }
                .onFailure { logger.debug(TAG, "tombstone purge skipped for ${tracker.id}: ${it.message}") }
        }
    }

    /** A completed sync both links the account and retires any prompt that was waiting on it. */
    private suspend fun markAccountLinked(uid: String) {
        preferences.markLinked(uid)
        preferences.clearPendingLink()
    }

    private suspend fun dropOrphans() {
        receiptDao.deleteOrphans()
        sourceDao.deleteOrphans()
    }

    private suspend fun recordOutcome(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.Success -> identity.currentUid()?.let {
                preferences.recordSyncSuccess(it, now())
            }
            is SyncOutcome.Retryable -> preferences.recordSyncError(outcome.message)
            is SyncOutcome.Fatal -> preferences.recordSyncError(outcome.message)
            is SyncOutcome.Blocked -> if (outcome.reason == SyncBlockReason.NOT_SIGNED_IN ||
                outcome.reason == SyncBlockReason.AUTO_SYNC_DISABLED
            ) preferences.clearSyncError()
        }
    }

    /**
     * Splits failures into "try again later" and "asking again will not help".
     *
     * Getting this wrong is expensive in both directions: retrying a permission error burns
     * battery on a doomed backoff schedule, and giving up on a transient network blip strands
     * the user's changes on the device.
     */
    private fun classify(e: Throwable): SyncOutcome {
        logger.error(TAG, "sync", e)
        return when {
            e is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.INTERNAL,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                FirebaseFirestoreException.Code.CANCELLED ->
                    SyncOutcome.Retryable(e.friendlyMessage(), e)

                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    SyncOutcome.Retryable("Session expired. Sign in again to resume syncing.", e)

                else -> SyncOutcome.Fatal(e.friendlyMessage(), e)
            }

            e is UnknownHostException || e is IOException ->
                SyncOutcome.Retryable("No connection. Changes are saved and will sync later.", e)

            else -> SyncOutcome.Retryable(e.message ?: "Sync failed", e)
        }
    }

    private fun FirebaseFirestoreException.friendlyMessage(): String = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You no longer have access to some of this data."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Cloud is unreachable. Changes are saved and will sync later."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Sync quota reached. It will retry shortly."
        else -> message ?: "Sync failed"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** The sync bookkeeping fields, viewed uniformly across the three entity types. */
    private interface SyncRow {
        val pendingSync: Boolean
        val remoteUpdatedAt: Long?
        val updatedAt: Long
    }

    private fun decide(local: TrackerEntity?, remoteUpdatedAt: Long, mode: SyncMode) =
        decide(local?.asSyncRow(), remoteUpdatedAt, mode)

    private fun decide(local: SourceEntity?, remoteUpdatedAt: Long, mode: SyncMode) =
        decide(local?.asSyncRow(), remoteUpdatedAt, mode)

    private fun decide(local: ReceiptEntity?, remoteUpdatedAt: Long, mode: SyncMode) =
        decide(local?.asSyncRow(), remoteUpdatedAt, mode)

    private fun TrackerEntity.asSyncRow() = object : SyncRow {
        override val pendingSync = this@asSyncRow.pendingSync
        override val remoteUpdatedAt = this@asSyncRow.remoteUpdatedAt
        override val updatedAt = this@asSyncRow.updatedAt
    }

    private fun SourceEntity.asSyncRow() = object : SyncRow {
        override val pendingSync = this@asSyncRow.pendingSync
        override val remoteUpdatedAt = this@asSyncRow.remoteUpdatedAt
        override val updatedAt = this@asSyncRow.updatedAt
    }

    private fun ReceiptEntity.asSyncRow() = object : SyncRow {
        override val pendingSync = this@asSyncRow.pendingSync
        override val remoteUpdatedAt = this@asSyncRow.remoteUpdatedAt
        override val updatedAt = this@asSyncRow.updatedAt
    }

    private fun RemoteTracker.toCleanEntity() = TrackerEntity(
        id = id,
        name = name,
        ownerId = ownerId,
        sharedWith = sharedWith,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        pendingSync = false,
        remoteUpdatedAt = updatedAt
    )

    private fun RemoteSource.toCleanEntity() = SourceEntity(
        id = id,
        trackerId = trackerId,
        name = name,
        type = parseType(type) ?: TransactionType.EXPENSE,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        pendingSync = false,
        remoteUpdatedAt = updatedAt
    )

    private fun RemoteReceipt.toCleanEntity(type: TransactionType) = ReceiptEntity(
        id = id,
        trackerId = trackerId,
        sourceId = sourceId,
        type = type,
        name = name,
        description = description,
        amount = amount,
        date = if (date > 0) date else createdAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        pendingSync = false,
        remoteUpdatedAt = updatedAt
    )

    private fun TrackerEntity.toRemote(grandTotal: Double) = RemoteTracker(
        id = id,
        name = name,
        ownerId = ownerId,
        sharedWith = sharedWith,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        grandTotal = grandTotal
    )

    private fun SourceEntity.toRemote(totalAmount: Double) = RemoteSource(
        id = id,
        trackerId = trackerId,
        name = name,
        type = type.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted,
        totalAmount = totalAmount
    )

    private fun ReceiptEntity.toRemote() = RemoteReceipt(
        id = id,
        trackerId = trackerId,
        sourceId = sourceId,
        type = type.name,
        name = name,
        description = description,
        amount = amount,
        date = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deleted = deleted
    )

    /**
     * A receipt whose stored type is missing or unrecognised inherits its source's type, which
     * is right far more often than any fixed default — and matters because the type decides
     * whether the amount adds to or subtracts from every total above it.
     */
    private fun resolveTypeFor(receipt: RemoteReceipt, sources: List<RemoteSource>): TransactionType =
        parseType(receipt.type)
            ?: sources.firstOrNull { it.id == receipt.sourceId }?.let { parseType(it.type) }
            ?: TransactionType.EXPENSE

    private fun parseType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    private fun conflictOf(
        type: ConflictEntityType,
        entityId: String,
        trackerId: String,
        label: String,
        localSummary: String,
        remoteSummary: String,
        localDeleted: Boolean,
        remoteDeleted: Boolean,
        payload: String
    ) = SyncConflictEntity(
        id = SyncConflictEntity.keyOf(type, entityId),
        entityType = type,
        entityId = entityId,
        trackerId = trackerId,
        label = label,
        localSummary = localSummary,
        remoteSummary = remoteSummary,
        localDeleted = localDeleted,
        remoteDeleted = remoteDeleted,
        remotePayload = payload,
        detectedAt = now()
    )

    private fun summaryOf(t: TrackerEntity) = if (t.deleted) "Deleted" else "Named \"${t.name}\""
    private fun summaryOf(t: RemoteTracker) = if (t.deleted) "Deleted" else "Named \"${t.name}\""
    private fun summaryOf(s: SourceEntity) = if (s.deleted) "Deleted" else "\"${s.name}\" · ${s.type.name.lowercase()}"
    private fun summaryOf(s: RemoteSource) = if (s.deleted) "Deleted" else "\"${s.name}\" · ${s.type.lowercase()}"
    private fun summaryOf(r: ReceiptEntity) =
        if (r.deleted) "Deleted" else "\"${r.name}\" · ${formatAmount(r.amount)}"
    private fun summaryOf(r: RemoteReceipt) =
        if (r.deleted) "Deleted" else "\"${r.name}\" · ${formatAmount(r.amount)}"

    private fun formatAmount(value: Double): String = String.format("%.2f", abs(value))

    private companion object {
        const val TAG = "SyncEngine"

        /** How long a tombstone is kept so slower devices still see the deletion. */
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
