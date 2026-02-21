package com.xs.expensetracker.utils.events

sealed class TransactionUiEvent {
    data class Loading(val message: String) : TransactionUiEvent()
    data object Success : TransactionUiEvent()
    data class Error(val message: String) : TransactionUiEvent()
}