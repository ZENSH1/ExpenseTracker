package com.xs.expensetracker.domain.data.models

import com.xs.expensetracker.domain.data.enums.TransactionType

/**
 * [type] is non-null: it decides the sign of every total in the app, and a null would have to
 * be guessed at each call site. Remote documents with a missing or unrecognised type fall back
 * to their source's type during import rather than propagating a null inward.
 */
data class TransactionReceipt(
    val id: String = "",
    val trackerId: String = "",
    val sourceId: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val name: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
)
