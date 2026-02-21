package com.xs.expensetracker.utils.events

sealed class TrackerUiEvent {
    data class Loading(val message: String) : TrackerUiEvent()
    data object Success : TrackerUiEvent()
    data class Error(val message: String) : TrackerUiEvent()
}