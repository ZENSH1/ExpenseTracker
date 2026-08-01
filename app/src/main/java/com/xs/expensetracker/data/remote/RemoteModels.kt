package com.xs.expensetracker.data.remote

import com.xs.expensetracker.domain.data.enums.TransactionType
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the cloud copy.
 *
 * These are deliberately separate from both the Room entities and the domain models: the
 * remote schema has to stay readable by app versions that predate offline mode, and it carries
 * fields (`deleted`, `updatedAt`) that mean something only to the sync engine.
 *
 * They are `@Serializable` because a conflicting remote record is stored verbatim as JSON in
 * the conflicts table, so "keep the cloud version" can be applied later — possibly offline —
 * without going back to the network.
 */
@Serializable
data class RemoteTracker(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val sharedWith: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    /**
     * Denormalised net balance. The app derives totals locally and never reads this back; it
     * exists so older clients and anything reading Firestore directly still see a balance.
     */
    val grandTotal: Double = 0.0
)

@Serializable
data class RemoteSource(
    val id: String = "",
    val trackerId: String = "",
    val name: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    /** Denormalised, write-only — see [RemoteTracker.grandTotal]. */
    val totalAmount: Double = 0.0
)

@Serializable
data class RemoteReceipt(
    val id: String = "",
    val trackerId: String = "",
    val sourceId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val name: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false
)

/** One pull's worth of remote changes. */
data class RemoteSnapshot(
    val trackers: List<RemoteTracker> = emptyList(),
    val sources: List<RemoteSource> = emptyList(),
    val receipts: List<RemoteReceipt> = emptyList()
) {
    val isEmpty: Boolean get() = trackers.isEmpty() && sources.isEmpty() && receipts.isEmpty()
}

/** What to push in one round. Deletes are expressed as tombstoned records, not absences. */
data class RemotePush(
    val trackers: List<RemoteTracker> = emptyList(),
    val sources: List<RemoteSource> = emptyList(),
    val receipts: List<RemoteReceipt> = emptyList()
) {
    val isEmpty: Boolean get() = trackers.isEmpty() && sources.isEmpty() && receipts.isEmpty()
}
