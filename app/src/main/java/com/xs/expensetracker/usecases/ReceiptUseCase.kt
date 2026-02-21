package com.xs.expensetracker.usecases

import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.events.TransactionUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReceiptUseCase(
    private val repository: ExpenseTrackerRepository
) {

    fun observeReceipts(
        trackerId: String,
        sourceId: String
    ): Flow<List<TransactionReceipt>> =
        repository.observeReceipts(trackerId, sourceId)

    fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank() || sourceId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid tracker or source"))
            return@flow
        }
        if (name.isBlank()) {
            emit(TransactionUiEvent.Error("Receipt name cannot be empty"))
            return@flow
        }
        if (amount <= 0.0) {
            emit(TransactionUiEvent.Error("Amount must be greater than zero"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Adding receipt..."))
        repository.addReceipt(trackerId, sourceId, type, name.trim(), description, amount, date).fold(
            onSuccess = { emit(TransactionUiEvent.Success) },
            onFailure = { emit(TransactionUiEvent.Error(it.message ?: "Failed to add receipt")) }
        )
    }

    fun updateReceipt(
        trackerId: String,
        sourceId: String,
        receipt: TransactionReceipt
    ): Flow<TransactionUiEvent> = flow {
        if (receipt.id.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid receipt"))
            return@flow
        }
        if (receipt.name.isBlank()) {
            emit(TransactionUiEvent.Error("Receipt name cannot be empty"))
            return@flow
        }
        if (receipt.amount <= 0.0) {
            emit(TransactionUiEvent.Error("Amount must be greater than zero"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Updating receipt..."))
        repository.updateReceipt(trackerId, sourceId, receipt).fold(
            onSuccess = { emit(TransactionUiEvent.Success) },
            onFailure = { emit(TransactionUiEvent.Error(it.message ?: "Failed to update receipt")) }
        )
    }

    fun deleteReceipt(
        trackerId: String,
        sourceId: String,
        receiptId: String
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank() || sourceId.isBlank() || receiptId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid receipt reference"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Deleting receipt..."))
        repository.deleteReceipt(trackerId, sourceId, receiptId).fold(
            onSuccess = { emit(TransactionUiEvent.Success) },
            onFailure = { emit(TransactionUiEvent.Error(it.message ?: "Failed to delete receipt")) }
        )
    }
}