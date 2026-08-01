package com.xs.expensetracker.data.prefs

import com.xs.expensetracker.data.sync.SyncIdentity
import com.xs.expensetracker.domain.repo.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Answers "who owns the records this device creates right now?".
 *
 * Signed in, that is the Firebase uid. Signed out — which is a perfectly normal, fully
 * supported state — it is the device-local id from [SyncPreferences]. Everything above this
 * class works with a plain owner id and never has to branch on whether an account exists.
 */
class IdentityProvider(
    private val authRepository: AuthRepository,
    private val preferences: SyncPreferences
) : SyncIdentity {

    /** Firebase uid, or `null` when the user is using the app without an account. */
    override fun currentUid(): String? = authRepository.currentUser?.uid

    override suspend fun currentOwnerId(): String = currentUid() ?: preferences.localUserId()

    fun observeOwnerId(): Flow<String> = authRepository.observeAuthState().map { user ->
        user?.uid ?: preferences.localUserId()
    }

    fun observeUid(): Flow<String?> = authRepository.observeAuthState().map { it?.uid }
}
