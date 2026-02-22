package com.xs.expensetracker.usecases

import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.AppLogger
import com.xs.expensetracker.utils.events.TransactionUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ReceiptUseCase(
    private val repository: ExpenseTrackerRepository,
    private val logger: AppLogger
) {

    // sourceId = null or "" → all receipts across every source in the tracker
    fun observeReceipts(
        trackerId: String,
        sourceId: String?
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
            val msg = "Invalid tracker or source"
            logger.error(TAG, "addReceipt", msg, mapOf("tracker_id" to trackerId, "source_id" to sourceId))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        if (name.isBlank()) {
            val msg = "Receipt name cannot be empty"
            logger.error(TAG, "addReceipt", msg, mapOf("tracker_id" to trackerId, "source_id" to sourceId))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        if (amount <= 0.0) {
            val msg = "Amount must be greater than zero"
            logger.error(TAG, "addReceipt", msg, mapOf("tracker_id" to trackerId, "amount" to amount.toString()))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "addReceipt → trackerId=$trackerId sourceId=$sourceId name=$name amount=$amount")
        emit(TransactionUiEvent.Loading("Adding receipt..."))
        repository.addReceipt(trackerId, sourceId, type, name.trim(), description, amount, date).fold(
            onSuccess = {
                logger.event("receipt_added", mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "type"       to type.name
                ))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "addReceipt", it, mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "type"       to type.name
                ))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to add receipt"))
            }
        )
    }

    fun updateReceipt(
        trackerId: String,
        sourceId: String,
        receipt: TransactionReceipt
    ): Flow<TransactionUiEvent> = flow {
        if (receipt.id.isBlank()) {
            val msg = "Invalid receipt"
            logger.error(TAG, "updateReceipt", msg, mapOf("tracker_id" to trackerId, "source_id" to sourceId))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        if (receipt.name.isBlank()) {
            val msg = "Receipt name cannot be empty"
            logger.error(TAG, "updateReceipt", msg, mapOf("receipt_id" to receipt.id))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        if (receipt.amount <= 0.0) {
            val msg = "Amount must be greater than zero"
            logger.error(TAG, "updateReceipt", msg, mapOf("receipt_id" to receipt.id, "amount" to receipt.amount.toString()))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "updateReceipt → receiptId=${receipt.id} amount=${receipt.amount}")
        emit(TransactionUiEvent.Loading("Updating receipt..."))
        repository.updateReceipt(trackerId, sourceId, receipt).fold(
            onSuccess = {
                logger.event("receipt_updated", mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "receipt_id" to receipt.id
                ))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "updateReceipt", it, mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "receipt_id" to receipt.id
                ))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to update receipt"))
            }
        )
    }

    fun deleteReceipt(
        trackerId: String,
        sourceId: String,
        receiptId: String
    ): Flow<TransactionUiEvent> = flow {
        if (trackerId.isBlank() || sourceId.isBlank() || receiptId.isBlank()) {
            val msg = "Invalid receipt reference"
            logger.error(TAG, "deleteReceipt", msg, mapOf(
                "tracker_id" to trackerId,
                "source_id"  to sourceId,
                "receipt_id" to receiptId
            ))
            emit(TransactionUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "deleteReceipt → trackerId=$trackerId sourceId=$sourceId receiptId=$receiptId")
        emit(TransactionUiEvent.Loading("Deleting receipt..."))
        repository.deleteReceipt(trackerId, sourceId, receiptId).fold(
            onSuccess = {
                logger.event("receipt_deleted", mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "receipt_id" to receiptId
                ))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "deleteReceipt", it, mapOf(
                    "tracker_id" to trackerId,
                    "source_id"  to sourceId,
                    "receipt_id" to receiptId
                ))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to delete receipt"))
            }
        )
    }

    private companion object {
        const val TAG = "ReceiptUseCase"
    }
}