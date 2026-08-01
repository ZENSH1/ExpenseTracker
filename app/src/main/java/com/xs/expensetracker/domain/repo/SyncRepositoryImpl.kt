package com.xs.expensetracker.domain.repo

import androidx.work.WorkInfo
import com.xs.expensetracker.data.local.dao.SyncConflictDao
import com.xs.expensetracker.data.local.dao.SyncDao
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.prefs.SyncPreferences
import com.xs.expensetracker.data.sync.ConflictResolution
import com.xs.expensetracker.data.sync.SyncEngine
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.data.sync.SyncOutcome
import com.xs.expensetracker.data.sync.SyncScheduler
import com.xs.expensetracker.data.sync.SyncStatus
import com.xs.expensetracker.data.sync.SyncStatusFactory
import com.xs.expensetracker.data.sync.SyncUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SyncRepositoryImpl(
    private val syncEngine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val preferences: SyncPreferences,
    private val conflictDao: SyncConflictDao,
    private val syncDao: SyncDao,
    private val authRepository: AuthRepository
) : SyncRepository {

    override val conflicts: Flow<List<SyncConflictEntity>> = conflictDao.observeAll()

    /** Preference values, folded into one object so the top-level `combine` stays readable. */
    private data class PrefsSnapshot(
        val lastSyncedAt: Long?,
        val error: String?,
        val autoSync: Boolean,
        val wifiOnly: Boolean,
        val pendingLink: SyncPreferences.PendingLink?,
        val pendingAccountUid: String?
    )

    private val prefsFlow: Flow<PrefsSnapshot> = combine(
        preferences.lastSyncedAt,
        preferences.lastSyncError,
        preferences.autoSyncEnabled,
        preferences.wifiOnly,
        preferences.pendingLinkFlow
    ) { lastSyncedAt, error, autoSync, wifiOnly, pendingLink ->
        PrefsSnapshot(lastSyncedAt, error, autoSync, wifiOnly, pendingLink, null)
    }.let { partial ->
        combine(partial, preferences.pendingAccountUidFlow) { snapshot, pendingAccountUid ->
            snapshot.copy(pendingAccountUid = pendingAccountUid)
        }
    }

    private data class Counts(val pending: Int, val local: Int, val conflicts: Int)

    private val countsFlow: Flow<Counts> = combine(
        syncDao.observePendingCount(),
        syncDao.observeActiveRecordCount(),
        conflictDao.observeCount()
    ) { pending, local, conflicts -> Counts(pending, local, conflicts) }

    override val state: Flow<SyncUiState> = combine(
        authRepository.observeAuthState(),
        countsFlow,
        scheduler.observeWork(),
        prefsFlow
    ) { user, counts, workInfos, prefs ->
        val pendingCount = counts.pending
        val conflictCount = counts.conflicts
        SyncUiState(
            status = deriveStatus(
                isSignedIn = user != null,
                pendingCount = pendingCount,
                conflictCount = conflictCount,
                workInfos = workInfos,
                prefs = prefs
            ),
            isSignedIn = user != null,
            accountEmail = user?.email,
            accountName = user?.displayName,
            lastSyncedAt = prefs.lastSyncedAt,
            pendingCount = pendingCount,
            localRecordCount = counts.local,
            conflictCount = conflictCount,
            autoSyncEnabled = prefs.autoSync,
            wifiOnly = prefs.wifiOnly,
            pendingLink = prefs.pendingLink,
            pendingAccountUid = prefs.pendingAccountUid
        )
    }

    /** Reduces WorkManager's view of the world to three flags, then defers to the factory. */
    private fun deriveStatus(
        isSignedIn: Boolean,
        pendingCount: Int,
        conflictCount: Int,
        workInfos: List<WorkInfo>,
        prefs: PrefsSnapshot
    ): SyncStatus = SyncStatusFactory.derive(
        isSignedIn = isSignedIn,
        pendingCount = pendingCount,
        conflictCount = conflictCount,
        isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING },
        // An enqueued run that has already attempted at least once is a backoff, not a fresh
        // request — worth telling the user apart from "queued".
        isBackingOff = workInfos.any {
            it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0
        },
        hasFailed = workInfos.any { it.state == WorkInfo.State.FAILED },
        errorMessage = prefs.error,
        pendingAccountUid = prefs.pendingAccountUid,
        pendingLinkLocalRecords = prefs.pendingLink?.localRecords,
        pendingLinkRemoteTrackers = prefs.pendingLink?.remoteTrackers
    )

    // ── Actions ──────────────────────────────────────────────────────────────

    override suspend fun requestSync() {
        if (authRepository.currentUser == null) return
        scheduler.requestSync()
    }

    override suspend fun syncNow(): SyncOutcome = syncEngine.sync(manual = true)

    override suspend fun importFromCloud(): SyncOutcome =
        syncEngine.sync(mode = SyncMode.FORCE_DOWNLOAD, manual = true)

    override suspend fun exportToCloud(): SyncOutcome =
        syncEngine.sync(mode = SyncMode.FORCE_UPLOAD, manual = true)

    override suspend fun resolveLinkDecision(mode: SyncMode): SyncOutcome =
        syncEngine.resolveLinkDecision(mode)

    override suspend fun resolveAccountChange(keepLocalData: Boolean): SyncOutcome =
        syncEngine.resolveAccountChange(keepLocalData)

    override suspend fun resolveConflict(conflictId: String, resolution: ConflictResolution) {
        syncEngine.resolveConflict(conflictId, resolution)
        scheduler.requestSync()
    }

    override suspend fun resolveAllConflicts(resolution: ConflictResolution) {
        syncEngine.resolveAllConflicts(resolution)
        scheduler.requestSync()
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        preferences.setAutoSyncEnabled(enabled)
        if (enabled) scheduler.schedulePeriodicSync() else scheduler.cancelAll()
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        preferences.setWifiOnly(enabled)
        // Constraints are baked into an enqueued request, so the change only takes effect once
        // the work is re-created.
        scheduler.schedulePeriodicSync()
    }

    /**
     * Local data deliberately survives sign-out — it stays usable offline. `lastSyncedUid` is
     * kept too: it is exactly what lets the next sign-in notice a *different* account and ask
     * before mixing the two.
     */
    override suspend fun onSignedOut() {
        scheduler.cancelAll()
        preferences.clearSyncError()
    }

    override suspend fun onSignedIn() {
        scheduler.schedulePeriodicSync()
        scheduler.requestSync(manual = false, expedited = true)
    }

    override suspend fun purgeCloudData(): Result<Unit> = syncEngine.purgeCloudData()

    override suspend fun resetSyncState() {
        scheduler.cancelAll()
        preferences.resetSyncState()
        conflictDao.deleteAll()
    }
}
