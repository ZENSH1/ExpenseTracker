package com.xs.expensetracker.data.remote

/**
 * The cloud half of sync. Deliberately a small set of coarse, suspending calls — no listeners
 * and no Flows — because the local database is what the UI observes. Nothing above this
 * interface knows Firestore exists, which is also what makes the sync engine unit-testable.
 */
interface RemoteExpenseDataSource {

    /**
     * Every tracker visible to [uid]. Kept separate from [fetchChanges] because the sync
     * engine needs the tracker list before it can decide anything else, and because "does the
     * cloud already have data for this account?" is answered by this call alone.
     */
    suspend fun fetchTrackers(uid: String): List<RemoteTracker>

    /**
     * Records changed at or after [since]. `since = 0` fetches everything.
     *
     * [trackerIds] scopes the child queries; callers pass the ids from [fetchTrackers] so a
     * tracker that itself did not change still has its sources and receipts examined.
     */
    suspend fun fetchChanges(uid: String, trackerIds: List<String>, since: Long): RemoteSnapshot

    /** Writes records and tombstones. Chunked internally to stay under Firestore batch limits. */
    suspend fun push(push: RemotePush)

    /** Hard-deletes a tracker and everything under it. Used for account deletion. */
    suspend fun deleteTrackerTree(trackerId: String)

    /** Permanently removes tombstones older than [before]; only the owner may do this. */
    suspend fun purgeTombstones(trackerId: String, before: Long)
}
