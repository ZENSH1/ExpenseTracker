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
import com.xs.expensetracker.utils.states.ActionResult
import com.xs.expensetracker.utils.states.ActionTarget
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

    /** Distinguishes one success from the next when both carry the same message. */
    private var nextResultId = 1L

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
            trackerUseCase.createTracker(name).collect { handleTrackerEvent("Tracker created", it) }
        }
    }

    fun updateTrackerName(trackerId: String, newName: String) {
        viewModelScope.launch {
            trackerUseCase.updateTrackerName(trackerId, newName)
                .collect { handleTrackerEvent("Tracker renamed", it) }
        }
    }

    fun shareTracker(trackerId: String, userIdToShare: String) {
        viewModelScope.launch {
            trackerUseCase.shareTracker(trackerId, userIdToShare)
                .collect { handleTrackerEvent("Tracker shared", it) }
        }
    }

    fun deleteTracker(trackerId: String) {
        viewModelScope.launch {
            trackerUseCase.deleteTracker(trackerId)
                .collect { handleTrackerEvent("Tracker deleted", it) }
        }
    }

    private fun handleTrackerEvent(successMessage: String, event: TrackerUiEvent) {
        when (event) {
            is TrackerUiEvent.Loading -> _uiState.update {
                it.copy(isLoading = true, error = null, loadingMessage = event.message)
            }
            is TrackerUiEvent.Success -> publishSuccess(ActionTarget.TRACKER, successMessage)
            is TrackerUiEvent.Error -> _uiState.update {
                it.copy(isLoading = false, error = event.message, loadingMessage = null)
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
            sourceUseCase.createSource(trackerId, name, type)
                .collect { handleTransactionEvent(ActionTarget.SOURCE, "Source created", it) }
        }
    }

    fun updateSource(sourceId: String, name: String, type: TransactionType) {
        viewModelScope.launch {
            sourceUseCase.updateSource(sourceId, name, type)
                .collect { handleTransactionEvent(ActionTarget.SOURCE, "Source updated", it) }
        }
    }

    fun deleteSource(trackerId: String, sourceId: String) {
        viewModelScope.launch {
            sourceUseCase.deleteSource(trackerId, sourceId)
                .collect { handleTransactionEvent(ActionTarget.SOURCE, "Source deleted", it) }
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
                .collect { handleTransactionEvent(ActionTarget.RECEIPT, "Receipt added", it) }
        }
    }

    fun updateReceipt(receipt: TransactionReceipt) {
        viewModelScope.launch {
            receiptUseCase.updateReceipt(receipt)
                .collect { handleTransactionEvent(ActionTarget.RECEIPT, "Receipt updated", it) }
        }
    }

    fun deleteReceipt(receiptId: String) {
        viewModelScope.launch {
            receiptUseCase.deleteReceipt(receiptId)
                .collect { handleTransactionEvent(ActionTarget.RECEIPT, "Receipt deleted", it) }
        }
    }

    private fun handleTransactionEvent(
        target: ActionTarget,
        successMessage: String,
        event: TransactionUiEvent
    ) {
        when (event) {
            is TransactionUiEvent.Loading -> _uiState.update {
                it.copy(isLoading = true, error = null, loadingMessage = event.message)
            }
            is TransactionUiEvent.Success -> publishSuccess(target, successMessage)
            is TransactionUiEvent.Error -> _uiState.update {
                it.copy(isLoading = false, error = event.message, loadingMessage = null)
            }
        }
    }

    /**
     * Records a completed write. The id is taken outside the `update` block because that
     * lambda can be re-run on contention, which would burn two ids for one result.
     */
    private fun publishSuccess(target: ActionTarget, message: String) {
        val result = ActionResult(nextResultId++, target, message)
        _uiState.update { it.copy(isLoading = false, loadingMessage = null, lastResult = result) }
    }

    // ------------------------------------------------
    // UTILITY
    // ------------------------------------------------

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Called once a screen has shown the confirmation, so it is not shown again. */
    fun consumeResult() {
        _uiState.update { it.copy(lastResult = null) }
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
