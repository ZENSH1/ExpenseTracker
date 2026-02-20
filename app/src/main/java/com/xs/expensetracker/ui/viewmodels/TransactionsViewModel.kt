package com.xs.expensetracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.states.TransactionsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: ExpenseTrackerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var observeSourcesJob: Job? = null
    private var observeReceiptsJob: Job? = null

    // ------------------------------------------------
    // SOURCES
    // ------------------------------------------------

    fun observeSources(
        trackerId: String,
        type: TransactionType? = null
    ) {
        observeSourcesJob?.cancel()

        observeSourcesJob = repository
            .observeSources(trackerId, type)
            .onEach { sources ->
                _uiState.update { it.copy(sources = sources) }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(error = e.message)
                }
            }
            .launchIn(viewModelScope)
    }

    fun createSource(
        trackerId: String,
        name: String,
        type: TransactionType
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = repository.createSource(
                trackerId,
                name,
                type
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteSource(
        trackerId: String,
        sourceId: String
    ) {
        viewModelScope.launch {
            repository.deleteSource(trackerId, sourceId)
                .onFailure { error->
                    _uiState.update {
                        it.copy(error = error.message)
                    }
                }
        }
    }

    // ------------------------------------------------
    // RECEIPTS
    // ------------------------------------------------

    fun observeReceipts(
        trackerId: String,
        sourceId: String
    ) {
        observeReceiptsJob?.cancel()

        observeReceiptsJob = repository
            .observeReceipts(trackerId, sourceId)
            .onEach { receipts ->
                _uiState.update { it.copy(receipts = receipts) }
            }
            .catch { e ->
                _uiState.update {
                    it.copy(error = e.message)
                }
            }
            .launchIn(viewModelScope)
    }

    fun addReceipt(
        trackerId: String,
        sourceId: String,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = repository.addReceipt(
                trackerId,
                sourceId,
                name,
                description,
                amount,
                date
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateReceipt(
        trackerId: String,
        sourceId: String,
        receipt: TransactionReceipt
    ) {
        viewModelScope.launch {
            repository.updateReceipt(trackerId, sourceId, receipt)
                .onFailure { error->
                    _uiState.update {
                        it.copy(error = error.message)
                    }
                }
        }
    }

    fun deleteReceipt(
        trackerId: String,
        sourceId: String,
        receiptId: String
    ) {
        viewModelScope.launch {
            repository.deleteReceipt(
                trackerId,
                sourceId,
                receiptId
            ).onFailure { error->
                _uiState.update {
                    it.copy(error = error.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}