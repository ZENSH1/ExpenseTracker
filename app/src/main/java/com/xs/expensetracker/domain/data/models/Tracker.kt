package com.xs.expensetracker.domain.data.models

import kotlinx.serialization.Serializable

/**
 * A tracker as the UI sees it.
 *
 * [grandTotal] is derived from the tracker's receipts on every read rather than stored, so it
 * is always consistent with the rows beneath it. [pendingChanges] counts records in this
 * tracker that have not reached the cloud yet — zero when everything is synced, and also zero
 * for a user who never signs in and simply has nowhere to sync to.
 */
@Serializable
data class Tracker(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val grandTotal: Double = 0.0,   // Income total − Expense total
    val sharedWith: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingChanges: Int = 0
)
