package com.xs.expensetracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.sync.ConflictResolution
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.data.sync.SyncOutcome
import com.xs.expensetracker.data.sync.SyncUiState
import com.xs.expensetracker.domain.repo.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncRepository: SyncRepository
) : ViewModel() {

    val state: StateFlow<SyncUiState> = syncRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    val conflicts: StateFlow<List<SyncConflictEntity>> = syncRepository.conflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Result of the most recent explicit action, for a one-shot message in the UI. */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /** Guards the destructive import/export buttons from double taps. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun syncNow() = runAction { syncRepository.syncNow() }

    fun importFromCloud() = runAction { syncRepository.importFromCloud() }

    fun exportToCloud() = runAction { syncRepository.exportToCloud() }

    fun resolveLinkDecision(mode: SyncMode) = runAction { syncRepository.resolveLinkDecision(mode) }

    fun resolveAccountChange(keepLocalData: Boolean) =
        runAction { syncRepository.resolveAccountChange(keepLocalData) }

    fun resolveConflict(conflictId: String, resolution: ConflictResolution) {
        viewModelScope.launch { syncRepository.resolveConflict(conflictId, resolution) }
    }

    fun resolveAllConflicts(resolution: ConflictResolution) {
        viewModelScope.launch { syncRepository.resolveAllConflicts(resolution) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { syncRepository.setAutoSyncEnabled(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { syncRepository.setWifiOnly(enabled) }
    }

    fun consumeMessage() {
        _actionMessage.value = null
    }

    private fun runAction(block: suspend () -> SyncOutcome) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _actionMessage.value = describe(block())
            _isBusy.value = false
        }
    }

    private fun describe(outcome: SyncOutcome): String = when (outcome) {
        is SyncOutcome.Success -> buildString {
            append("Synced")
            val parts = buildList {
                if (outcome.pushed > 0) add("${outcome.pushed} uploaded")
                if (outcome.pulled > 0) add("${outcome.pulled} downloaded")
                if (outcome.conflicts > 0) add("${outcome.conflicts} need review")
            }
            if (parts.isNotEmpty()) append(" · ${parts.joinToString(", ")}")
        }

        is SyncOutcome.Blocked -> when (outcome.reason) {
            com.xs.expensetracker.data.sync.SyncBlockReason.NOT_SIGNED_IN ->
                "Sign in to sync with the cloud."
            com.xs.expensetracker.data.sync.SyncBlockReason.AUTO_SYNC_DISABLED ->
                "Auto-sync is off. Use Sync now to sync manually."
            com.xs.expensetracker.data.sync.SyncBlockReason.NEEDS_LINK_DECISION ->
                "Choose which copy of your data to keep."
            com.xs.expensetracker.data.sync.SyncBlockReason.NEEDS_ACCOUNT_DECISION ->
                "A different account signed in. Choose what to do with the data on this device."
        }

        is SyncOutcome.Retryable -> "${outcome.message} Will retry automatically."
        is SyncOutcome.Fatal -> outcome.message
    }
}
