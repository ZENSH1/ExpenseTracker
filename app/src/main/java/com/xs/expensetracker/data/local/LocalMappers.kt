package com.xs.expensetracker.data.local

import com.xs.expensetracker.data.local.entity.ReceiptEntity
import com.xs.expensetracker.data.local.entity.SourceWithTotal
import com.xs.expensetracker.data.local.entity.TrackerWithTotals
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource

fun TrackerWithTotals.toDomain(): Tracker = Tracker(
    id = tracker.id,
    name = tracker.name,
    ownerId = tracker.ownerId,
    grandTotal = grandTotal,
    sharedWith = tracker.sharedWith,
    createdAt = tracker.createdAt,
    updatedAt = tracker.updatedAt,
    pendingChanges = pendingCount
)

fun SourceWithTotal.toDomain(): TransactionSource = TransactionSource(
    id = source.id,
    trackerId = source.trackerId,
    name = source.name,
    type = source.type,
    totalAmount = totalAmount,
    createdAt = source.createdAt,
    updatedAt = source.updatedAt,
    pendingSync = source.pendingSync
)

fun ReceiptEntity.toDomain(): TransactionReceipt = TransactionReceipt(
    id = id,
    trackerId = trackerId,
    sourceId = sourceId,
    type = type,
    name = name,
    description = description,
    amount = amount,
    date = date,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)
