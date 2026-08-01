package com.xs.expensetracker.domain.repo

import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.sync.ConflictResolution
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.data.sync.SyncOutcome
import com.xs.expensetracker.data.sync.SyncUiState
import kotlinx.coroutines.flow.Flow

/** Everything the UI can observe or ask of the sync layer. */
interface SyncRepository {

    val state: Flow<SyncUiState>

    val conflicts: Flow<List<SyncConflictEntity>>

    /** Queues a background sync — used after edits. Does nothing useful when signed out. */
    suspend fun requestSync()

    /** "Sync now": expedited, ignores the auto-sync toggle, and pulls the full history. */
    suspend fun syncNow(): SyncOutcome

    /** "Import from cloud" — replaces local data with the cloud copy. */
    suspend fun importFromCloud(): SyncOutcome

    /** "Export to cloud" — rewrites the cloud copy to match this device. */
    suspend fun exportToCloud(): SyncOutcome

    /** Answers the first-sync prompt. */
    suspend fun resolveLinkDecision(mode: SyncMode): SyncOutcome

    /** Answers the account-switch prompt. */
    suspend fun resolveAccountChange(keepLocalData: Boolean): SyncOutcome

    suspend fun resolveConflict(conflictId: String, resolution: ConflictResolution)

    suspend fun resolveAllConflicts(resolution: ConflictResolution)

    suspend fun setAutoSyncEnabled(enabled: Boolean)

    suspend fun setWifiOnly(enabled: Boolean)

    /** Called after sign-out so background work stops and the next sign-in re-links cleanly. */
    suspend fun onSignedOut()

    /** Called after sign-in to kick off the first reconcile. */
    suspend fun onSignedIn()

    /**
     * Erases the user's cloud data ahead of account deletion. Must run while still
     * authenticated — once the account is gone, the documents are unreachable and would be
     * orphaned in Firestore forever.
     */
    suspend fun purgeCloudData(): Result<Unit>

    /** Forgets sync bookkeeping so the next sign-in starts from a clean slate. */
    suspend fun resetSyncState()
}
