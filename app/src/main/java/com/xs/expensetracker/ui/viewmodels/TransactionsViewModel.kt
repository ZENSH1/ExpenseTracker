package com.xs.expensetracker.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.usecases.ReceiptUseCase
import com.xs.expensetracker.usecases.SourceUseCase
import com.xs.expensetracker.usecases.TrackerUseCase
import com.xs.expensetracker.utils.ExportManager
import com.xs.expensetracker.utils.events.TrackerUiEvent
import com.xs.expensetracker.utils.events.TransactionUiEvent
import com.xs.expensetracker.utils.states.TransactionsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionsViewModel(
    private val trackerUseCase: TrackerUseCase,
    private val sourceUseCase: SourceUseCase,
    private val receiptUseCase: ReceiptUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var observeTrackersJob: Job? = null
    private var observeSourcesJob: Job? = null
    private var observeReceiptsJob: Job? = null

    private var observeTrackerJob: Job? = null

    // ------------------------------------------------
    // TRACKERS
    // ------------------------------------------------

    fun observeTracker(trackerId: String) {
        observeTrackerJob?.cancel()
        _uiState.update { it.copy(selectedTracker = null) }
        observeTrackerJob = trackerUseCase
            .observeTracker(trackerId)
            .onEach { tracker -> _uiState.update { it.copy(selectedTracker = tracker) } }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun observeTrackers(userId: String) {
        observeTrackersJob?.cancel()
        observeTrackersJob = trackerUseCase
            .observeTrackers(userId)
            .onEach { trackers -> _uiState.update { it.copy(trackers = trackers) } }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun createTracker(name: String, ownerId: String) {
        viewModelScope.launch {
            trackerUseCase.createTracker(name, ownerId).collect {}
        }
    }

    fun updateTrackerName(trackerId: String, newName: String) {
        viewModelScope.launch {
            trackerUseCase.updateTrackerName(trackerId, newName).collect { event ->
                handleTrackerEvent(event)
            }
        }
    }

    fun shareTracker(trackerId: String, userIdToShare: String) {
        viewModelScope.launch {
            trackerUseCase.shareTracker(trackerId, userIdToShare).collect { event ->
                handleTrackerEvent(event)
            }
        }
    }

    fun deleteTracker(trackerId: String) {
        viewModelScope.launch {
            trackerUseCase.deleteTracker(trackerId).collect { event ->
                handleTrackerEvent(event)
            }
        }
    }

    private fun handleTrackerEvent(event: TrackerUiEvent) {
        _uiState.update {
            when (event) {
                is TrackerUiEvent.Loading -> it.copy(isLoading = true, error = null, loadingMessage = event.message)
                is TrackerUiEvent.Success -> it.copy(isLoading = false, loadingMessage = null)
                is TrackerUiEvent.Error   -> it.copy(isLoading = false, error = event.message, loadingMessage = null)
            }
        }
    }

    // ------------------------------------------------
    // SOURCES
    // ------------------------------------------------

    fun observeSources(trackerId: String, type: TransactionType? = null) {
        observeSourcesJob?.cancel()
        _uiState.update { it.copy(sources = emptyList()) }
        observeSourcesJob = sourceUseCase
            .observeSources(trackerId, type)
            .onEach { sources -> _uiState.update { it.copy(sources = sources) } }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun createSource(trackerId: String, name: String, type: TransactionType) {
        viewModelScope.launch {
            sourceUseCase.createSource(trackerId, name, type).collect { event ->
                handleTransactionEvent(event)
            }
        }
    }

    fun deleteSource(trackerId: String, sourceId: String) {
        viewModelScope.launch {
            sourceUseCase.deleteSource(trackerId, sourceId).collect { event ->
                handleTransactionEvent(event)
            }
        }
    }

    // ------------------------------------------------
    // RECEIPTS
    // ------------------------------------------------

    /**
     * sourceId = null or ""  →  observe ALL receipts across every source in the tracker.
     * sourceId non-blank     →  observe receipts for that specific source only.
     */
    fun observeReceipts(trackerId: String, sourceId: String?) {
        observeReceiptsJob?.cancel()
        _uiState.update { it.copy(receipts = emptyList()) }
        observeReceiptsJob = receiptUseCase
            .observeReceipts(trackerId, sourceId)
            .onEach { receipts -> _uiState.update { it.copy(receipts = receipts) } }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun addReceipt(
        trackerId: String,
        sourceId: String,
        type: TransactionType,
        name: String,
        description: String,
        amount: Double,
        date: Long
    ) {
        viewModelScope.launch {
            receiptUseCase.addReceipt(trackerId, sourceId, type, name, description, amount, date)
                .collect { event -> handleTransactionEvent(event) }
        }
    }

    fun updateReceipt(trackerId: String, sourceId: String, receipt: TransactionReceipt) {
        viewModelScope.launch {
            receiptUseCase.updateReceipt(trackerId, sourceId, receipt)
                .collect { event -> handleTransactionEvent(event) }
        }
    }

    fun deleteReceipt(trackerId: String, sourceId: String, receiptId: String) {
        viewModelScope.launch {
            receiptUseCase.deleteReceipt(trackerId, sourceId, receiptId)
                .collect { event -> handleTransactionEvent(event) }
        }
    }

    private fun handleTransactionEvent(event: TransactionUiEvent) {
        _uiState.update {
            when (event) {
                is TransactionUiEvent.Loading -> it.copy(isLoading = true, error = null, loadingMessage = event.message)
                is TransactionUiEvent.Success -> it.copy(isLoading = false, loadingMessage = null)
                is TransactionUiEvent.Error   -> it.copy(isLoading = false, error = event.message, loadingMessage = null)
            }
        }
    }

    // ------------------------------------------------
    // UTILITY
    // ------------------------------------------------

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    fun clearAllObservers(){
        observeTrackersJob?.cancel()
        observeSourcesJob?.cancel()
        observeReceiptsJob?.cancel()
    }

    // ------------------------------------------------
    // EXPORT
    // ------------------------------------------------

    /**
     * Collect ALL sources + receipts for the tracker (regardless of active
     * type filter), then generate a CSV in the app's cache directory.
     * Returns a FileProvider URI the caller can use for ACTION_SEND, or
     * null if generation fails.
     */
    suspend fun exportToCsv(context: Context, tracker: Tracker): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val allSources  = sourceUseCase.observeSources(tracker.id, null).first()
                val allReceipts = receiptUseCase.observeReceipts(tracker.id, null).first()
                ExportManager.exportToCsv(context.applicationContext, tracker, allSources, allReceipts)
            }.getOrNull()
        }

    /**
     * Same as above but produces a PDF.
     */
    suspend fun exportToPdf(context: Context, tracker: Tracker): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val allSources  = sourceUseCase.observeSources(tracker.id, null).first()
                val allReceipts = receiptUseCase.observeReceipts(tracker.id, null).first()
                ExportManager.exportToPdf(context.applicationContext, tracker, allSources, allReceipts)
            }.getOrNull()
        }
}