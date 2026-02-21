package com.xs.expensetracker.utils.states

import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.data.models.TransactionSource

data class TransactionsUiState(
    val trackers: List<Tracker> = emptyList(),
    val sources: List<TransactionSource> = emptyList(),
    val receipts: List<TransactionReceipt> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,     // granular loading text from use cases
    val error: String? = null
)