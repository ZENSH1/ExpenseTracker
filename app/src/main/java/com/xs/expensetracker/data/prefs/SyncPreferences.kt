package com.xs.expensetracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/**
 * Everything the sync layer needs to remember between runs.
 *
 * The most important value here is [localUserId]. The app is usable with no account at all, so
 * records created offline still need an owner; they get this device-local id, which is later
 * rewritten to the Firebase uid when the user signs in and claims the data.
 */
class SyncPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val LOCAL_USER_ID = stringPreferencesKey("local_user_id")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNCED_UID = stringPreferencesKey("last_synced_uid")
        val LAST_PULL_AT = longPreferencesKey("last_pull_at")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val LINKED_UIDS = stringPreferencesKey("linked_uids")
        val PENDING_ACCOUNT_UID = stringPreferencesKey("pending_account_uid")
        val PENDING_LINK_UID = stringPreferencesKey("pending_link_uid")
        val PENDING_LINK_LOCAL = longPreferencesKey("pending_link_local_count")
        val PENDING_LINK_REMOTE = longPreferencesKey("pending_link_remote_count")
    }

    // ── Device identity ──────────────────────────────────────────────────────

    /**
     * Stable pseudonymous id for this install, generated on first use. Prefixed so it can never
     * be mistaken for a Firebase uid — the sync engine relies on telling the two apart to know
     * which records still need claiming.
     */
    suspend fun localUserId(): String {
        dataStore.data.first()[Keys.LOCAL_USER_ID]?.let { return it }
        val generated = LOCAL_ID_PREFIX + UUID.randomUUID().toString()
        // Re-check inside edit: two callers racing on first launch must agree on one id.
        return dataStore.edit { it[Keys.LOCAL_USER_ID] = it[Keys.LOCAL_USER_ID] ?: generated }
            .let { it[Keys.LOCAL_USER_ID] ?: generated }
    }

    val localUserIdFlow: Flow<String?> = dataStore.data.map { it[Keys.LOCAL_USER_ID] }

    // ── User-facing toggles ──────────────────────────────────────────────────

    val autoSyncEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_SYNC] ?: true }
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }

    suspend fun autoSyncEnabledOnce(): Boolean = autoSyncEnabled.first()

    suspend fun wifiOnlyOnce(): Boolean = wifiOnly.first()

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    // ── Sync bookkeeping ─────────────────────────────────────────────────────

    val lastSyncedAt: Flow<Long?> = dataStore.data.map { it[Keys.LAST_SYNCED_AT]?.takeIf { v -> v > 0 } }
    val lastSyncError: Flow<String?> = dataStore.data.map { it[Keys.LAST_SYNC_ERROR] }

    suspend fun lastSyncedUid(): String? = dataStore.data.first()[Keys.LAST_SYNCED_UID]

    /**
     * Watermark for incremental pulls. Reads back slightly earlier than it was written (see
     * [PULL_SAFETY_WINDOW_MS]) because `updatedAt` values come from whichever device wrote
     * them, and a peer with a lagging clock could otherwise slip under the watermark and be
     * skipped forever.
     */
    suspend fun lastPullAt(): Long =
        (dataStore.data.first()[Keys.LAST_PULL_AT] ?: 0L)
            .let { if (it <= 0L) 0L else (it - PULL_SAFETY_WINDOW_MS).coerceAtLeast(0L) }

    suspend fun setLastPullAt(value: Long) {
        dataStore.edit { it[Keys.LAST_PULL_AT] = value }
    }

    suspend fun recordSyncSuccess(uid: String, at: Long) {
        dataStore.edit {
            it[Keys.LAST_SYNCED_AT] = at
            it[Keys.LAST_SYNCED_UID] = uid
            it.remove(Keys.LAST_SYNC_ERROR)
        }
    }

    suspend fun recordSyncError(message: String) {
        dataStore.edit { it[Keys.LAST_SYNC_ERROR] = message }
    }

    suspend fun clearSyncError() {
        dataStore.edit { it.remove(Keys.LAST_SYNC_ERROR) }
    }

    // ── Account linking ──────────────────────────────────────────────────────
    //
    // A uid is "linked" once its first sync has completed on this device. That flag is what
    // distinguishes "first sync for this account — local and cloud data may both exist and the
    // user must choose" from every subsequent incremental sync.

    suspend fun linkedUids(): Set<String> =
        dataStore.data.first()[Keys.LINKED_UIDS]
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    suspend fun isLinked(uid: String): Boolean = uid in linkedUids()

    suspend fun markLinked(uid: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LINKED_UIDS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            prefs[Keys.LINKED_UIDS] = (current + uid).distinct().joinToString(",")
        }
    }

    /**
     * Forgetting a uid forces the next sync with that account back through the first-sync
     * decision. Used when the user resets sync state or discards local data.
     */
    suspend fun unlink(uid: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.LINKED_UIDS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            prefs[Keys.LINKED_UIDS] = (current - uid).joinToString(",")
        }
    }

    /** The uid whose account-switch prompt is currently awaiting an answer. */
    suspend fun pendingAccountUid(): String? = dataStore.data.first()[Keys.PENDING_ACCOUNT_UID]

    val pendingAccountUidFlow: Flow<String?> = dataStore.data.map { it[Keys.PENDING_ACCOUNT_UID] }

    suspend fun setPendingAccountUid(uid: String?) {
        dataStore.edit {
            if (uid == null) it.remove(Keys.PENDING_ACCOUNT_UID) else it[Keys.PENDING_ACCOUNT_UID] = uid
        }
    }

    /**
     * A pending first-sync decision, recorded durably because it is raised inside a background
     * worker: the UI has to be able to discover it later, from a cold start if need be.
     */
    data class PendingLink(val uid: String, val localRecords: Int, val remoteTrackers: Int)

    val pendingLinkFlow: Flow<PendingLink?> = dataStore.data.map { prefs ->
        prefs[Keys.PENDING_LINK_UID]?.let {
            PendingLink(
                uid = it,
                localRecords = (prefs[Keys.PENDING_LINK_LOCAL] ?: 0L).toInt(),
                remoteTrackers = (prefs[Keys.PENDING_LINK_REMOTE] ?: 0L).toInt()
            )
        }
    }

    suspend fun setPendingLink(uid: String, localRecords: Int, remoteTrackers: Int) {
        dataStore.edit {
            it[Keys.PENDING_LINK_UID] = uid
            it[Keys.PENDING_LINK_LOCAL] = localRecords.toLong()
            it[Keys.PENDING_LINK_REMOTE] = remoteTrackers.toLong()
        }
    }

    suspend fun clearPendingLink() {
        dataStore.edit {
            it.remove(Keys.PENDING_LINK_UID)
            it.remove(Keys.PENDING_LINK_LOCAL)
            it.remove(Keys.PENDING_LINK_REMOTE)
        }
    }

    /** Wipes sync bookkeeping without touching the device identity or user toggles. */
    suspend fun resetSyncState() {
        dataStore.edit {
            it.remove(Keys.LAST_SYNCED_AT)
            it.remove(Keys.LAST_SYNCED_UID)
            it.remove(Keys.LAST_PULL_AT)
            it.remove(Keys.LAST_SYNC_ERROR)
            it.remove(Keys.LINKED_UIDS)
            it.remove(Keys.PENDING_ACCOUNT_UID)
            it.remove(Keys.PENDING_LINK_UID)
            it.remove(Keys.PENDING_LINK_LOCAL)
            it.remove(Keys.PENDING_LINK_REMOTE)
        }
    }

    companion object {
        const val LOCAL_ID_PREFIX = "local:"

        /** How far back a pull reaches beyond the recorded watermark, to absorb clock skew. */
        const val PULL_SAFETY_WINDOW_MS = 10 * 60 * 1000L

        fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_ID_PREFIX)
    }
}
