package com.xs.expensetracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xs.expensetracker.domain.data.enums.TransactionType

/**
 * A single transaction. Receipts are the only rows that carry money, so every total in the app
 * is an aggregate over this table.
 */
@Entity(
    tableName = "receipts",
    indices = [Index("trackerId"), Index("sourceId"), Index("pendingSync"), Index("date")]
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val trackerId: String,
    val sourceId: String,
    val type: TransactionType,
    val name: String,
    val description: String = "",
    val amount: Double,
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val pendingSync: Boolean = true,
    val remoteUpdatedAt: Long? = null
)
