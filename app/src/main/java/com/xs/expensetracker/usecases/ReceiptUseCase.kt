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

    fun observeReceipts(
        trackerId: String,
        sourceId: String?
    ): Flow<List<TransactionReceipt>> = repository.observeReceipts(trackerId, sourceId)

    suspend fun getReceiptsForExport(trackerId: String): List<TransactionReceipt> =
        repository.getReceiptsForExport(trackerId)

    fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ): Flow<TransactionUiEvent> = flow {
        validateReceiptInput(trackerId, sourceId, name, amount)?.let {
            emit(TransactionUiEvent.Error(it))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Adding receipt..."))
        repository.addReceipt(trackerId, sourceId, type, name.trim(), description, amount, date).fold(
            onSuccess = {
                logger.event(
                    "receipt_added",
                    mapOf("tracker_id" to trackerId, "source_id" to sourceId, "type" to type.name)
                )
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "addReceipt", it, mapOf("tracker_id" to trackerId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to add receipt"))
            }
        )
    }

    fun updateReceipt(receipt: TransactionReceipt): Flow<TransactionUiEvent> = flow {
        if (receipt.id.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid receipt"))
            return@flow
        }
        validateReceiptInput(receipt.trackerId, receipt.sourceId, receipt.name, receipt.amount)?.let {
            emit(TransactionUiEvent.Error(it))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Updating receipt..."))
        repository.updateReceipt(receipt).fold(
            onSuccess = {
                logger.event("receipt_updated", mapOf("receipt_id" to receipt.id))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "updateReceipt", it, mapOf("receipt_id" to receipt.id))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to update receipt"))
            }
        )
    }

    fun deleteReceipt(receiptId: String): Flow<TransactionUiEvent> = flow {
        if (receiptId.isBlank()) {
            emit(TransactionUiEvent.Error("Invalid receipt reference"))
            return@flow
        }
        emit(TransactionUiEvent.Loading("Deleting receipt..."))
        repository.deleteReceipt(receiptId).fold(
            onSuccess = {
                logger.event("receipt_deleted", mapOf("receipt_id" to receiptId))
                emit(TransactionUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "deleteReceipt", it, mapOf("receipt_id" to receiptId))
                emit(TransactionUiEvent.Error(it.message ?: "Failed to delete receipt"))
            }
        )
    }

    private companion object {
        const val TAG = "ReceiptUseCase"
    }
}

/**
 * Shared field checks for add and update.
 *
 * Returns the first problem found, or `null` when the input is usable. NaN and infinity are
 * rejected explicitly: both slip past a `> 0` test on some paths and would poison every
 * aggregate that later sums the column.
 */
internal fun validateReceiptInput(
    trackerId: String,
    sourceId: String,
    name: String,
    amount: Double
): String? = when {
    trackerId.isBlank() || sourceId.isBlank() -> "Invalid tracker or source"
    name.isBlank() -> "Receipt name cannot be empty"
    !amount.isFinite() -> "Amount is not a valid number"
    amount <= 0.0 -> "Amount must be greater than zero"
    else -> null
}
