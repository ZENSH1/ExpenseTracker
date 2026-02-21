package com.xs.expensetracker.data.models

data class Tracker(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val grandTotal: Double = 0.0,   // Income total − Expense total
    val sharedWith: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)