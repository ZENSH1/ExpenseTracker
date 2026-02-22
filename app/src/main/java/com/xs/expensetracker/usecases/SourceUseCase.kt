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
    ): Flow<List<TransactionSource>> =
        repository.observeSources(trackerId, type)

    fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank()) {
            val msg = "Invalid tracker"
            logger.error(TAG, "createSource", msg)
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        if (name.isBlank()) {
            val msg = "Source name cannot be empty"
            logger.error(TAG, "createSource", msg, mapOf("tracker_id" to trackerId))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "createSource → trackerId=$trackerId name=$name type=$type")
        emit(TransactionUiEvent.Loading("Creating source..."))
        repository.createSource(trackerId, name.trim(), type).fold(
            onSuccess = {
                logger.event("source_created", mapOf("tracker_id" to trackerId, "type" to type.name))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "createSource", it, mapOf("tracker_id" to trackerId, "type" to type.name))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to create source"))
            }
        )
    }

    fun deleteSource(
        trackerId: String,
        sourceId: String
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank() || sourceId.isBlank()) {
            val msg = "Invalid tracker or source"
            logger.error(TAG, "deleteSource", msg, mapOf("tracker_id" to trackerId, "source_id" to sourceId))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "deleteSource → trackerId=$trackerId sourceId=$sourceId")
        emit(TransactionUiEvent.Loading("Deleting source..."))
        repository.deleteSource(trackerId, sourceId).fold(
            onSuccess = {
                logger.event("source_deleted", mapOf("tracker_id" to trackerId, "source_id" to sourceId))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "deleteSource", it, mapOf("tracker_id" to trackerId, "source_id" to sourceId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to delete source"))
            }
        )
    }

    private companion object {
        const val TAG = "SourceUseCase"
    }
}