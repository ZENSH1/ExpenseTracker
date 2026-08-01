package com.xs.expensetracker.usecases

import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.AppLogger
import com.xs.expensetracker.utils.events.TrackerUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrackerUseCase(
    private val repository: ExpenseTrackerRepository,
    private val logger: AppLogger
) {

    fun observeTracker(trackerId: String): Flow<Tracker?> = repository.observeTracker(trackerId)

    fun observeTrackers(): Flow<List<Tracker>> = repository.observeTrackers()

    fun createTracker(name: String): Flow<TrackerUiEvent> = flow {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            emit(TrackerUiEvent.Error("Tracker name cannot be empty"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Creating tracker..."))
        repository.createTracker(trimmed).fold(
            onSuccess = {
                logger.event("tracker_created")
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "createTracker", it)
                emit(TrackerUiEvent.Error(it.message ?: "Failed to create tracker"))
            }
        )
    }

    fun updateTrackerName(trackerId: String, newName: String): Flow<TrackerUiEvent> = flow {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            emit(TrackerUiEvent.Error("Tracker name cannot be empty"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Updating tracker..."))
        repository.updateTrackerName(trackerId, trimmed).fold(
            onSuccess = {
                logger.event("tracker_renamed", mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "updateTrackerName", it, mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Error(it.message ?: "Failed to update tracker"))
            }
        )
    }

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
            onSuccess = {
                logger.event("tracker_shared", mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "shareTracker", it, mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Error(it.message ?: "Failed to share tracker"))
            }
        )
    }

    fun deleteTracker(trackerId: String): Flow<TrackerUiEvent> = flow {
        if (trackerId.isBlank()) {
            emit(TrackerUiEvent.Error("Invalid tracker"))
            return@flow
        }
        emit(TrackerUiEvent.Loading("Deleting tracker..."))
        repository.deleteTracker(trackerId).fold(
            onSuccess = {
                logger.event("tracker_deleted", mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "deleteTracker", it, mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Error(it.message ?: "Failed to delete tracker"))
            }
        )
    }

    private companion object {
        const val TAG = "TrackerUseCase"
    }
}
