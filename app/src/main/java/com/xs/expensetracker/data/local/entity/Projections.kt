package com.xs.expensetracker.data.local.entity

import androidx.room.Embedded

/**
 * A tracker plus its net balance, computed by SQL rather than stored.
 *
 * [pendingCount] powers the "n unsynced changes" affordance without a second query.
 */
data class TrackerWithTotals(
    @Embedded val tracker: TrackerEntity,
    val grandTotal: Double,
    val pendingCount: Int
)

/** A source plus the sum of its (non-deleted) receipts. */
data class SourceWithTotal(
    @Embedded val source: SourceEntity,
    val totalAmount: Double
)
