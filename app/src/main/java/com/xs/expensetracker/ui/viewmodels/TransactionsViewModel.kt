package com.xs.expensetracker.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.data.prefs.IdentityProvider
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.data.models.TransactionReceipt
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionsViewModel(
    private val trackerUseCase: TrackerUseCase,
    private val sourceUseCase: SourceUseCase,
    private val receiptUseCase: ReceiptUseCase,
    private val identityProvider: IdentityProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var observeTrackersJob: Job? = null
    private var observeSourcesJob: Job? = null
    private var observeReceiptsJob: Job? = null
    private var observeTrackerJob: Job? = null

    init {
        // Ownership decides the "shared with you" badge and which menu actions appear. It has to
        // track sign-in state, since the same records are owned by a device id before an account
        // exists and by the uid afterwards.
        identityProvider.observeOwnerId()
            .onEach { owner -> _uiState.update { it.copy(currentOwnerId = owner) } }
            .catch { /* identity is cosmetic here; never surface it as a data error */ }
            .launchIn(viewModelScope)
    }

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

    fun observeTrackers() {
        observeTrackersJob?.cancel()
        observeTrackersJob = trackerUseCase
            .observeTrackers()
            .onEach { trackers ->
                _uiState.update { it.copy(trackers = trackers, isInitialLoad = false) }
            }
            .catch { e -> _uiState.update { it.copy(error = e.message, isInitialLoad = false) } }
            .launchIn(viewModelScope)
    }

    fun createTracker(name: String) {
        viewModelScope.launch {
            trackerUseCase.createTracker(name).collect(::handleTrackerEvent)
        }
    }

    fun updateTrackerName(trackerId: String, newName: String) {
        viewModelScope.launch {
            trackerUseCase.updateTrackerName(trackerId, newName).collect(::handleTrackerEvent)
        }
    }

    fun shareTracker(trackerId: String, userIdToShare: String) {
        viewModelScope.launch {
            trackerUseCase.shareTracker(trackerId, userIdToShare).collect(::handleTrackerEvent)
        }
    }

    fun deleteTracker(trackerId: String) {
        viewModelScope.launch {
            trackerUseCase.deleteTracker(trackerId).collect(::handleTrackerEvent)
        }
    }

    private fun handleTrackerEvent(event: TrackerUiEvent) {
        _uiState.update {
            when (event) {
                is TrackerUiEvent.Loading -> it.copy(isLoading = true, error = null, loadingMessage = event.message)
                is TrackerUiEvent.Success -> it.copy(isLoading = false, loadingMessage = null)
                is TrackerUiEvent.Error -> it.copy(isLoading = false, error = event.message, loadingMessage = null)
            }
        }
    }

    // ------------------------------------------------
    // SOURCES
    // ------------------------------------------------

    fun observeSources(trackerId: String, type: TransactionType? = null) {
        observeSourcesJob?.cancel()
        observeSourcesJob = sourceUseCase
            .observeSources(trackerId, type)
            .onEach { sources -> _uiState.update { it.copy(sources = sources) } }
            .catch { e -> _uiState.update { it.copy(error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun createSource(trackerId: String, name: String, type: TransactionType) {
        viewModelScope.launch {
            sourceUseCase.createSource(trackerId, name, type).collect(::handleTransactionEvent)
        }
    }

    fun updateSource(sourceId: String, name: String, type: TransactionType) {
        viewModelScope.launch {
            sourceUseCase.updateSource(sourceId, name, type).collect(::handleTransactionEvent)
        }
    }

    fun deleteSource(trackerId: String, sourceId: String) {
        viewModelScope.launch {
            sourceUseCase.deleteSource(trackerId, sourceId).collect(::handleTransactionEvent)
        }
    }

    // ------------------------------------------------
    // RECEIPTS
    // ------------------------------------------------

    /** [sourceId] null or blank observes every receipt in the tracker. */
    fun observeReceipts(trackerId: String, sourceId: String?) {
        observeReceiptsJob?.cancel()
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
                .collect(::handleTransactionEvent)
        }
    }

    fun updateReceipt(receipt: TransactionReceipt) {
        viewModelScope.launch {
            receiptUseCase.updateReceipt(receipt).collect(::handleTransactionEvent)
        }
    }

    fun deleteReceipt(receiptId: String) {
        viewModelScope.launch {
            receiptUseCase.deleteReceipt(receiptId).collect(::handleTransactionEvent)
        }
    }

    private fun handleTransactionEvent(event: TransactionUiEvent) {
        _uiState.update {
            when (event) {
                is TransactionUiEvent.Loading -> it.copy(isLoading = true, error = null, loadingMessage = event.message)
                is TransactionUiEvent.Success -> it.copy(isLoading = false, loadingMessage = null)
                is TransactionUiEvent.Error -> it.copy(isLoading = false, error = event.message, loadingMessage = null)
            }
        }
    }

    // ------------------------------------------------
    // UTILITY
    // ------------------------------------------------

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearAllObservers() {
        observeTrackersJob?.cancel()
        observeSourcesJob?.cancel()
        observeReceiptsJob?.cancel()
        observeTrackerJob?.cancel()
    }

    // ------------------------------------------------
    // EXPORT
    // ------------------------------------------------

    /**
     * Reads the tracker's full contents directly rather than reusing the observed lists, which
     * are narrowed by whatever filter the user has active on screen. Works offline — everything
     * it needs is already local.
     */
    suspend fun exportToCsv(context: Context, tracker: Tracker): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                ExportManager.exportToCsv(
                    context.applicationContext,
                    tracker,
                    sourceUseCase.getSourcesForExport(tracker.id),
                    receiptUseCase.getReceiptsForExport(tracker.id)
                )
            }.getOrNull()
        }

    suspend fun exportToPdf(context: Context, tracker: Tracker): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                ExportManager.exportToPdf(
                    context.applicationContext,
                    tracker,
                    sourceUseCase.getSourcesForExport(tracker.id),
                    receiptUseCase.getReceiptsForExport(tracker.id)
                )
            }.getOrNull()
        }
}
