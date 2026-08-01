package com.xs.expensetracker.data.sync

import com.xs.expensetracker.data.prefs.SyncPreferences

/**
 * What the sync symbol in the app bar is currently saying.
 *
 * Ordered by how much it wants the user's attention: a decision or a conflict outranks a
 * failure, which outranks routine progress.
 */
sealed interface SyncStatus {

    /** No account, so nothing to sync to. The app is fully usable in this state. */
    data object Offline : SyncStatus

    /** Signed in, everything pushed and pulled. */
    data object UpToDate : SyncStatus

    /** A sync is running right now. */
    data object Syncing : SyncStatus

    /** Local changes are waiting for the next run — including because there is no network. */
    data class Pending(val count: Int) : SyncStatus

    /** Failed, and WorkManager is backing off before the next attempt. */
    data class Retrying(val message: String) : SyncStatus

    /** Failed in a way retrying will not fix. */
    data class Failed(val message: String) : SyncStatus

    /** Records edited in both places; the user has to pick. */
    data class ConflictsPending(val count: Int) : SyncStatus

    /** First sync for this account, with data on both sides. */
    data class NeedsLinkDecision(val localRecords: Int, val remoteTrackers: Int) : SyncStatus

    /** A different account signed in while this device still holds the previous one's data. */
    data class NeedsAccountDecision(val previousUid: String?, val newUid: String) : SyncStatus
}

/** Everything the settings screen and the status indicator render from. */
data class SyncUiState(
    val status: SyncStatus = SyncStatus.Offline,
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val lastSyncedAt: Long? = null,
    /** Records not yet pushed to the cloud. */
    val pendingCount: Int = 0,
    /** Every record on this device, synced or not — what an account switch would affect. */
    val localRecordCount: Int = 0,
    val conflictCount: Int = 0,
    val autoSyncEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val pendingLink: SyncPreferences.PendingLink? = null,
    val pendingAccountUid: String? = null
) {
    /** Drives the spinning symbol. */
    val isSyncing: Boolean get() = status is SyncStatus.Syncing

    /** Whether anything needs the user rather than just the network. */
    val needsAttention: Boolean
        get() = status is SyncStatus.ConflictsPending ||
            status is SyncStatus.NeedsLinkDecision ||
            status is SyncStatus.NeedsAccountDecision ||
            status is SyncStatus.Failed
}
