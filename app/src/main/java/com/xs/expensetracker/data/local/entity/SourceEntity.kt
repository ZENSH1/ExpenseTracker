package com.xs.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xs.expensetracker.domain.data.enums.TransactionType

/**
 * A source of money within a tracker. `totalAmount` is derived from receipts, not stored —
 * see [com.xs.expensetracker.data.local.dao.SourceDao].
 *
 * No foreign key to [TrackerEntity] on purpose: sync pulls arrive in an arbitrary order and a
 * child can legitimately land before its parent. Referential integrity is enforced by the
 * repository's cascade logic instead, and orphans are swept up by
 * [com.xs.expensetracker.data.local.dao.SourceDao.deleteOrphans].
 */
@Entity(
    tableName = "sources",
    indices = [Index("trackerId"), Index("pendingSync")]
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val trackerId: String,
    val name: String,
    val type: TransactionType,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pendingSync: Boolean = true,
    val remoteUpdatedAt: Long? = null
)
