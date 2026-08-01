package com.xs.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local source of truth for a tracker.
 *
 * Sync bookkeeping columns, shared by every syncable entity:
 *
 * - [updatedAt]        Device-clock millis of the last *local* mutation. Also written to the
 *                      remote document, so it doubles as the version token other devices see.
 * - [deleted]          Soft-delete tombstone. Rows are never hard-deleted while they still need
 *                      to propagate, otherwise other devices would silently resurrect them.
 * - [pendingSync]      This row has local changes that have not reached the cloud yet.
 * - [remoteUpdatedAt]  The remote [updatedAt] this row was last reconciled against — the
 *                      *baseline* for three-way conflict detection. `null` means the row has
 *                      never existed remotely. Comparing against a stored baseline rather than
 *                      comparing timestamps to each other makes conflict detection immune to
 *                      device clock skew.
 *
 * Note that `grandTotal` is deliberately absent: totals are derived from receipts with SQL
 * aggregates (see [com.xs.expensetracker.data.local.dao.TrackerDao]) so they can never drift
 * out of step with the rows they summarise.
 */
@Entity(tableName = "trackers")
data class TrackerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ownerId: String,
    val sharedWith: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pendingSync: Boolean = true,
    val remoteUpdatedAt: Long? = null
)
