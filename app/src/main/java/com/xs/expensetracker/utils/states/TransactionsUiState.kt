package com.xs.expensetracker.utils.states

import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource

data class TransactionsUiState(
    val selectedTracker: Tracker? = null,
    val trackers: List<Tracker> = emptyList(),
    val sources: List<TransactionSource> = emptyList(),
    val receipts: List<TransactionReceipt> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,     // granular loading text from use cases
    val error: String? = null,
    /**
     * The most recent write that succeeded, kept until a screen consumes it. This is what
     * closes a form modal and puts up the "Receipt added" confirmation; see [ActionResult]
     * for why it is a held value rather than an edge on [isLoading].
     */
    val lastResult: ActionResult? = null,
    /**
     * Whoever owns records created on this device right now — a Firebase uid when signed in,
     * a device-local id otherwise. Used to tell "yours" from "shared with you".
     */
    val currentOwnerId: String = "",
    /**
     * True until the first database emission arrives. Distinguishes "still reading" from
     * "genuinely empty", which is the difference between a spinner and an empty state.
     */
    val isInitialLoad: Boolean = true
)
