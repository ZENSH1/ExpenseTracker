package com.xs.expensetracker.usecases

import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.events.TrackerUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrackerUseCase(
    private val repository: ExpenseTrackerRepository
) {

    /**
     * Real-time tracker list. Emits on every Firestore change.
     */
    fun observeTrackers(userId: String): Flow<List<Tracker>> =
        repository.observeTrackers(userId)

    /**
     * Create a tracker. Emits Loading → Success | Error.
     */
    fun createTracker(name: String, ownerId: String): Flow<TrackerUiEvent> = flow {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            emit(TrackerUiEvent.Error("Tracker name cannot be empty"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Creating tracker..."))
        repository.createTracker(trimmed, ownerId).fold(
            onSuccess = { emit(TrackerUiEvent.Success) },
            onFailure = { emit(TrackerUiEvent.Error(it.message ?: "Failed to create tracker")) }
        )
    }

    /**
     * Rename a tracker. Emits Loading → Success | Error.
     */
    fun updateTrackerName(trackerId: String, newName: String): Flow<TrackerUiEvent> = flow {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            emit(TrackerUiEvent.Error("Tracker name cannot be empty"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Updating tracker..."))
        repository.updateTrackerName(trackerId, trimmed).fold(
            onSuccess = { emit(TrackerUiEvent.Success) },
            onFailure = { emit(TrackerUiEvent.Error(it.message ?: "Failed to update tracker")) }
        )
    }

    /**
     * Share a tracker with another user. Emits Loading → Success | Error.
     */
    fun shareTracker(trackerId: String, userIdToShare: String): Flow<TrackerUiEvent> = flow {
        val trimmed = userIdToShare.trim()
        if (trimmed.isBlank()) {
            emit(TrackerUiEvent.Error("User ID cannot be empty"))
            return@flow
        }
        if (trackerId.isBlank()) {
            emit(TrackerUiEvent.Error("Invalid tracker"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Sharing tracker..."))
        repository.shareTracker(trackerId, trimmed).fold(
            onSuccess = { emit(TrackerUiEvent.Success) },
            onFailure = { emit(TrackerUiEvent.Error(it.message ?: "Failed to share tracker")) }
        )
    }

    /**
     * Delete a tracker. Emits Loading → Success | Error.
     */
    fun deleteTracker(trackerId: String): Flow<TrackerUiEvent> = flow {
        if (trackerId.isBlank()) {
            emit(TrackerUiEvent.Error("Invalid tracker"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Deleting tracker..."))
        repository.deleteTracker(trackerId).fold(
            onSuccess = { emit(TrackerUiEvent.Success) },
            onFailure = { emit(TrackerUiEvent.Error(it.message ?: "Failed to delete tracker")) }
        )
    }
}