package com.xs.expensetracker.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xs.expensetracker.data.prefs.SyncPreferences
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Owns every enqueue decision, so nothing else in the app has to know about WorkManager.
 *
 * Two pieces of work exist:
 *
 * - a unique one-shot ([WORK_SYNC_NOW]) for user actions and data changes, and
 * - a periodic pass ([WORK_SYNC_PERIODIC]) that catches up whatever the one-shots missed —
 *   changes made while offline, or a device that was in Doze when its retries ran out.
 */
class SyncScheduler(
    private val workManager: WorkManager,
    private val preferences: SyncPreferences
) {

    /**
     * Enqueues a sync.
     *
     * @param expedited for direct user actions ("Sync now"), which should not sit behind
     *   WorkManager's normal scheduling latency.
     */
    suspend fun requestSync(
        mode: SyncMode = SyncMode.NORMAL,
        manual: Boolean = false,
        expedited: Boolean = false
    ) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(SyncWorker.KEY_MODE to mode.name, SyncWorker.KEY_MANUAL to manual))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .apply {
                // Background-triggered syncs are debounced: a burst of edits should produce one
                // network round-trip, not one per keystroke.
                if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                else setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            }
            .addTag(TAG_SYNC)
            .build()

        workManager.enqueueUniqueWork(
            WORK_SYNC_NOW,
            // REPLACE so a fresh request supersedes a debouncing one; the engine's mutex keeps
            // an already-running pass from overlapping with the next.
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Registered once at startup; safe to call repeatedly. Suspends only to read preferences. */
    suspend fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_SYNC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_SYNC_PERIODIC,
            // KEEP, so an app restart does not reset the interval and re-sync immediately.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WORK_SYNC_NOW)
        workManager.cancelUniqueWork(WORK_SYNC_PERIODIC)
    }

    fun observeWork(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(TAG_SYNC)

    private suspend fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (preferences.wifiOnlyOnce()) NetworkType.UNMETERED else NetworkType.CONNECTED
        )
        .build()

    companion object {
        const val WORK_SYNC_NOW = "expense_sync_now"
        const val WORK_SYNC_PERIODIC = "expense_sync_periodic"
        const val TAG_SYNC = "expense_sync"

        private const val BACKOFF_SECONDS = 30L
        private const val DEBOUNCE_SECONDS = 5L
        private const val PERIOD_HOURS = 6L
    }
}
