package com.xs.expensetracker.data.sync

/** How a sync run should treat the difference between local and cloud. */
enum class SyncMode {
    /** Reconcile both directions; genuine two-sided edits become conflicts. */
    NORMAL,

    /** "Export to cloud" — local wins wholesale; every local record is re-pushed. */
    FORCE_UPLOAD,

    /** "Import from cloud" — cloud wins wholesale; local unsynced edits are discarded. */
    FORCE_DOWNLOAD,

    /** Union both sides, keeping the newer copy of any record that exists in both. */
    MERGE
}

/** Why the engine stopped without finishing. */
enum class SyncBlockReason {
    /** Nothing to sync to — the app works fine like this. */
    NOT_SIGNED_IN,

    /** Auto-sync is off; only an explicit "Sync now" proceeds. */
    AUTO_SYNC_DISABLED,

    /**
     * First sync for this account and both sides already hold data. The user must choose
     * before anything is written, so nothing is lost either way.
     */
    NEEDS_LINK_DECISION,

    /**
     * A different account signed in on a device that still holds the previous account's data.
     * Pushing blindly would upload one person's records into another person's cloud.
     */
    NEEDS_ACCOUNT_DECISION
}

/** Outcome of one sync run. */
sealed interface SyncOutcome {

    data class Success(
        val pushed: Int = 0,
        val pulled: Int = 0,
        val conflicts: Int = 0
    ) : SyncOutcome

    /** Stopped deliberately; retrying unchanged will stop the same way. */
    data class Blocked(
        val reason: SyncBlockReason,
        val localRecordCount: Int = 0,
        val remoteTrackerCount: Int = 0,
        val previousUid: String? = null,
        val newUid: String? = null
    ) : SyncOutcome

    /** Transient failure — network, timeout, backend unavailable. Worth retrying. */
    data class Retryable(val message: String, val cause: Throwable? = null) : SyncOutcome

    /** Permanent failure — permission denied, malformed data. Retrying will not help. */
    data class Fatal(val message: String, val cause: Throwable? = null) : SyncOutcome
}

/** How the user resolved a single conflicted record. */
enum class ConflictResolution { KEEP_LOCAL, KEEP_REMOTE }
