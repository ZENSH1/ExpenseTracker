package com.xs.expensetracker.domain.data.models

import com.xs.expensetracker.domain.data.enums.TransactionType

/** [totalAmount] is the sum of this source's receipts, computed on read. */
data class TransactionSource(
    val id: String = "",
    val trackerId: String = "",
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val totalAmount: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
)
