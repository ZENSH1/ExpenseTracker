package com.xs.expensetracker.data.sync

/**
 * Turns the raw signals into the single status the UI shows.
 *
 * Kept as a pure function, deliberately free of `WorkInfo`: the priority order below is the
 * part with real behaviour in it, and it should be testable without a WorkManager runtime.
 */
object SyncStatusFactory {

    /**
     * Ordered by urgency. Anything only the user can resolve outranks anything the app can
     * still fix by itself, so a conflict is never hidden behind a spinner — and a failure is
     * never hidden behind a pending count.
     */
    fun derive(
        isSignedIn: Boolean,
        pendingCount: Int,
        conflictCount: Int,
        isRunning: Boolean,
        isBackingOff: Boolean,
        hasFailed: Boolean,
        errorMessage: String?,
        pendingAccountUid: String?,
        pendingLinkLocalRecords: Int?,
        pendingLinkRemoteTrackers: Int?
    ): SyncStatus {
        if (!isSignedIn) return SyncStatus.Offline

        if (pendingAccountUid != null) {
            return SyncStatus.NeedsAccountDecision(previousUid = null, newUid = pendingAccountUid)
        }
        if (pendingLinkLocalRecords != null && pendingLinkRemoteTrackers != null) {
            return SyncStatus.NeedsLinkDecision(pendingLinkLocalRecords, pendingLinkRemoteTrackers)
        }
        if (conflictCount > 0) return SyncStatus.ConflictsPending(conflictCount)

        if (isRunning) return SyncStatus.Syncing
        if (isBackingOff) return SyncStatus.Retrying(errorMessage ?: "Retrying…")
        if (hasFailed) return SyncStatus.Failed(errorMessage ?: "Sync failed")

        if (pendingCount > 0) return SyncStatus.Pending(pendingCount)
        return SyncStatus.UpToDate
    }
}
