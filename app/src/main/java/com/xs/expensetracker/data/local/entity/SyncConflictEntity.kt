package com.xs.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which table a conflicted row belongs to. */
enum class ConflictEntityType { TRACKER, SOURCE, RECEIPT }

/**
 * A row that was edited both locally and in the cloud since the last successful reconcile.
 *
 * Neither side is applied automatically: the local row keeps its local value (and stays out of
 * the push queue) until the user chooses. [remotePayload] is the JSON-serialised remote record,
 * kept verbatim so "keep cloud" can be applied later without another network round-trip — which
 * matters because the user may resolve the conflict while offline.
 *
 * The primary key is `"<entityType>:<entityId>"` so repeated syncs refresh one row rather than
 * piling up duplicates for the same record.
 */
@Entity(
    tableName = "sync_conflicts",
    indices = [Index("trackerId")]
)
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val entityType: ConflictEntityType,
    val entityId: String,
    val trackerId: String,
    /** Human-readable name of the record, for the resolution UI. */
    val label: String,
    val localSummary: String,
    val remoteSummary: String,
    val localDeleted: Boolean,
    val remoteDeleted: Boolean,
    /** JSON of the remote record, replayed verbatim when the user keeps the cloud version. */
    val remotePayload: String,
    val detectedAt: Long
) {
    companion object {
        fun keyOf(type: ConflictEntityType, entityId: String) = "${type.name}:$entityId"
    }
}
