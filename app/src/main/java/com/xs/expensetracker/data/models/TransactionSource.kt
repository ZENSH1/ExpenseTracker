package com.xs.expensetracker.data.models

import com.xs.expensetracker.data.enums.TransactionType

data class TransactionSource(
    val id: String = "",
    val trackerId: String = "",
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val totalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)