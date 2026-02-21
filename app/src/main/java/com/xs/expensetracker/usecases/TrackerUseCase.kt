package com.xs.expensetracker.usecases

import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.repo.ExpenseTrackerRepository
import com.xs.expensetracker.utils.AppLogger
import com.xs.expensetracker.utils.events.TrackerUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrackerUseCase(
    private val repository: ExpenseTrackerRepository,
    private val logger: AppLogger
) {

    fun observeTrackers(userId: String): Flow<List<Tracker>> =
        repository.observeTrackers(userId)

    fun createTracker(name: String, ownerId: String): Flow<TrackerUiEvent> = flow {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            val msg = "Tracker name cannot be empty"
            logger.error(TAG, "createTracker", msg)
            emit(TrackerUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "createTracker → name=$trimmed ownerId=$ownerId")
        emit(TrackerUiEvent.Loading("Creating tracker..."))
        repository.createTracker(trimmed, ownerId).fold(
            onSuccess = {
                logger.event("tracker_created", mapOf("owner_id" to ownerId))
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "createTracker", it, mapOf("owner_id" to ownerId))
                emit(TrackerUiEvent.Error(it.message ?: "Failed to create tracker"))
            }
        )
    }

    fun updateTrackerName(trackerId: String, newName: String): Flow<TrackerUiEvent> = flow {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            val msg = "Tracker name cannot be empty"
            logger.error(TAG, "updateTrackerName", msg, mapOf("tracker_id" to trackerId))
            emit(TrackerUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "updateTrackerName → trackerId=$trackerId newName=$trimmed")
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
            val msg = "User ID cannot be empty"
            logger.error(TAG, "shareTracker", msg, mapOf("tracker_id" to trackerId))
            emit(TrackerUiEvent.Error(msg))
            return@flow
        }
        if (trackerId.isBlank()) {
            val msg = "Invalid tracker"
            logger.error(TAG, "shareTracker", msg)
            emit(TrackerUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "shareTracker → trackerId=$trackerId with=$trimmed")
        emit(TrackerUiEvent.Loading("Sharing tracker..."))
        repository.shareTracker(trackerId, trimmed).fold(
            onSuccess = {
                logger.event("tracker_shared", mapOf("tracker_id" to trackerId))
                emit(TrackerUiEvent.Success)
            },
            onFailure = {
                logger.error(TAG, "shareTracker", it, mapOf("tracker_id" to trackerId, "target_user" to trimmed))
                emit(TrackerUiEvent.Error(it.message ?: "Failed to share tracker"))
            }
        )
    }

    fun deleteTracker(trackerId: String): Flow<TrackerUiEvent> = flow {
        if (trackerId.isBlank()) {
            val msg = "Invalid tracker"
            logger.error(TAG, "deleteTracker", msg)
            emit(TrackerUiEvent.Error(msg))
            return@flow
        }
        logger.debug(TAG, "deleteTracker → trackerId=$trackerId")
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