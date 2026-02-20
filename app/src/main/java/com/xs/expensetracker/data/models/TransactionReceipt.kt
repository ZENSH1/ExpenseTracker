package com.xs.expensetracker.data.models

import com.xs.expensetracker.data.enums.TransactionType

data class TransactionReceipt(
    val id: String = "",
    val trackerId: String = "",
    val sourceId: String = "",
    val type: TransactionType? = null,
    val name: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)