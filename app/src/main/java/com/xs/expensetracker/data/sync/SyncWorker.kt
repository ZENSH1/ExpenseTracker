package com.xs.expensetracker.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs one [SyncEngine] pass under WorkManager's retry and constraint machinery.
 *
 * The worker itself holds no logic beyond translating a [SyncOutcome] into a WorkManager
 * result — everything interesting lives in the engine, where it can be tested without a
 * WorkManager runtime.
 *
 * Dependencies come from Koin rather than a custom `WorkerFactory`, which keeps WorkManager's
 * default initialisation intact.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncEngine: SyncEngine by inject()

    override suspend fun doWork(): Result {
        val mode = runCatching {
            SyncMode.valueOf(inputData.getString(KEY_MODE) ?: SyncMode.NORMAL.name)
        }.getOrDefault(SyncMode.NORMAL)
        val manual = inputData.getBoolean(KEY_MANUAL, false)

        return when (val outcome = syncEngine.sync(mode = mode, manual = manual)) {
            is SyncOutcome.Success -> Result.success(
                workDataOf(
                    KEY_PUSHED to outcome.pushed,
                    KEY_PULLED to outcome.pulled,
                    KEY_CONFLICTS to outcome.conflicts
                )
            )

            // Blocked runs stopped on purpose. Reporting failure would let WorkManager burn
            // through a backoff schedule for something only the user can unblock, and the
            // reason is already surfaced in the UI from the database and preferences.
            is SyncOutcome.Blocked -> Result.success(
                workDataOf(KEY_BLOCKED_REASON to outcome.reason.name)
            )

            is SyncOutcome.Retryable ->
                if (runAttemptCount >= MAX_ATTEMPTS) {
                    Result.failure(workDataOf(KEY_ERROR to outcome.message))
                } else {
                    Result.retry()
                }

            is SyncOutcome.Fatal -> Result.failure(workDataOf(KEY_ERROR to outcome.message))
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_MANUAL = "manual"
        const val KEY_PUSHED = "pushed"
        const val KEY_PULLED = "pulled"
        const val KEY_CONFLICTS = "conflicts"
        const val KEY_ERROR = "error"
        const val KEY_BLOCKED_REASON = "blocked_reason"

        /**
         * Caps the exponential backoff chain. With a 30s base that is roughly 30s, 1m, 2m, 4m,
         * … — about eight hours of retrying before the run is marked failed and left to the
         * next periodic pass. Local data is safe throughout; only its arrival in the cloud is
         * delayed.
         */
        const val MAX_ATTEMPTS = 10
    }
}
