package com.xs.expensetracker.usecases

import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.TransactionSource
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.AppLogger
import com.xs.expensetracker.utils.events.TransactionUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SourceUseCase(
    private val repository: ExpenseTrackerRepository,
    private val logger: AppLogger
) {

    fun observeSources(
        trackerId: String,
        type: TransactionType? = null
    ): Flow<List<TransactionSource>> = repository.observeSources(trackerId, type)

    suspend fun getSourcesForExport(trackerId: String): List<TransactionSource> =
        repository.getSourcesForExport(trackerId)

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
            onSuccess = {
                logger.event("source_created", mapOf("tracker_id" to trackerId, "type" to type.name))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "createSource", it, mapOf("tracker_id" to trackerId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to create source"))
            }
        )
    }

    fun updateSource(
        sourceId: String,
        name: String,
        type: TransactionType
    ): Flow<TransactionUiEvent> = flow {
        if (sourceId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid source"))
            return@flow
        }
        if (name.isBlank()) {
            emit(TransactionUiEvent.Error("Source name cannot be empty"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Updating source..."))
        repository.updateSource(sourceId, name.trim(), type).fold(
            onSuccess = {
                logger.event("source_updated", mapOf("source_id" to sourceId))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "updateSource", it, mapOf("source_id" to sourceId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to update source"))
            }
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
            onSuccess = {
                logger.event("source_deleted", mapOf("tracker_id" to trackerId, "source_id" to sourceId))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "deleteSource", it, mapOf("source_id" to sourceId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to delete source"))
            }
        )
    }

    private companion object {
        const val TAG = "SourceUseCase"
    }
}
