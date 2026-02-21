package com.xs.expensetracker.usecases

import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionSource
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.events.TransactionUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SourceUseCase(
    private val repository: ExpenseTrackerRepository
) {

    fun observeSources(
        trackerId: String,
        type: TransactionType? = null
    ): Flow<List<TransactionSource>> =
        repository.observeSources(trackerId, type)

    fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid tracker"))
            return@flow
        }
        if (name.isBlank()) {
            emit(TransactionUiEvent.Error("Source name cannot be empty"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Creating source..."))
        repository.createSource(trackerId, name.trim(), type).fold(
            onSuccess = { emit(TransactionUiEvent.Success) },
            onFailure = { emit(TransactionUiEvent.Error(it.message ?: "Failed to create source")) }
        )
    }

    fun deleteSource(
        trackerId: String,
        sourceId: String
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank() || sourceId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid tracker or source"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Deleting source..."))
        repository.deleteSource(trackerId, sourceId).fold(
            onSuccess = { emit(TransactionUiEvent.Success) },
            onFailure = { emit(TransactionUiEvent.Error(it.message ?: "Failed to delete source")) }
        )
    }
}