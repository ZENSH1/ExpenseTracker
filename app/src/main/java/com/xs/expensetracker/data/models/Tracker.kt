package com.xs.expensetracker.data.models

data class Tracker(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val sharedWith: List<String> = emptyList(), // other userIds
    val createdAt: Long = System.currentTimeMillis()
)