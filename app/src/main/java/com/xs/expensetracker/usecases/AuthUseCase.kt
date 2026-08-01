package com.xs.expensetracker.usecases

import android.app.Activity
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.xs.expensetracker.domain.repo.AuthRepository
import com.xs.expensetracker.domain.repo.ExpenseTrackerRepository
import com.xs.expensetracker.domain.repo.SyncRepository
import com.xs.expensetracker.utils.GoogleAuthManager
import com.xs.expensetracker.utils.states.AuthUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Signing in is optional in this app — it turns cloud sync on, nothing more. Nothing here gates
 * access to the user's data, which lives locally either way.
 */
class AuthUseCase(
    private val authRepository: AuthRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val syncRepository: SyncRepository,
    private val expenseRepository: ExpenseTrackerRepository
) {

    fun observeAuthState(): Flow<AuthUiState> =
        authRepository.observeAuthState().map { user ->
            if (user != null) AuthUiState.Authenticated(user) else AuthUiState.Unauthenticated
        }

    fun signIn(activity: Activity): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Talking to Google..."))
        val credential = googleAuthManager.signIn(activity)
        credential.fold(
            onSuccess = { idToken ->
                emit(AuthUiState.Loading("Signing in..."))
                emitAll(signInWithGoogle(idToken))
            },
            onFailure = { emit(AuthUiState.Error(it.message ?: "Auth failed")) }
        )
    }

    fun signInWithGoogle(idToken: String): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Checking credentials..."))
        authRepository.signInWithGoogle(idToken).fold(
            onSuccess = { user ->
                // Kicks off the first reconcile, which is also where the link decision (if any)
                // is raised. Failing to schedule must not fail the sign-in itself.
                runCatching { syncRepository.onSignedIn() }
                emit(AuthUiState.Authenticated(user))
            },
            onFailure = { emit(AuthUiState.Error(it.message ?: "Auth failed")) }
        )
    }

    /**
     * Local data deliberately survives sign-out: the app keeps working offline exactly as it
     * does for someone who never signed in.
     */
    fun signOut(): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Signing out..."))
        runCatching { syncRepository.onSignedOut() }
        authRepository.signOut()
        emit(AuthUiState.Unauthenticated)
    }

    /**
     * Deletes the account and everything belonging to it.
     *
     * Order matters: the cloud documents go first, while the credential is still valid. Doing
     * it the other way round would leave unreachable documents in Firestore for good.
     */
    fun deleteAccount(): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Deleting cloud data..."))
        val purge = syncRepository.purgeCloudData()
        if (purge.isFailure) {
            emit(
                AuthUiState.Error(
                    purge.exceptionOrNull()?.message
                        ?: "Couldn't remove your cloud data. Nothing was deleted."
                )
            )
            return@flow
        }

        emit(AuthUiState.Loading("Deleting account..."))
        authRepository.deleteAccount().fold(
            onSuccess = {
                runCatching { syncRepository.onSignedOut() }
                syncRepository.resetSyncState()
                expenseRepository.wipeLocalData()
                emit(AuthUiState.Unauthenticated)
            },
            onFailure = { error ->
                emit(
                    AuthUiState.Error(
                        // Firebase requires a fresh credential for destructive account
                        // operations; the generic message would leave the user stuck.
                        if (error is FirebaseAuthRecentLoginRequiredException) {
                            "For security, sign in again before deleting your account."
                        } else {
                            error.message ?: "Failed to delete account"
                        }
                    )
                )
            }
        )
    }
}
