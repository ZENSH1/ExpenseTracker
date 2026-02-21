package com.xs.expensetracker.usecases

import android.app.Activity
import com.xs.expensetracker.repo.AuthRepository
import com.xs.expensetracker.utils.GoogleAuthManager
import com.xs.expensetracker.utils.states.AuthUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class AuthUseCase(
    private val authRepository: AuthRepository,
    private val googleAuthManager: GoogleAuthManager
) {

    fun observeAuthState(): Flow<AuthUiState> =
        authRepository.observeAuthState().map { user ->
            if (user != null) AuthUiState.Authenticated(user)
            else AuthUiState.Unauthenticated
        }

    fun signIn(activity: Activity): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Talking to Google..."))
        runCatching {
            val result = googleAuthManager.signIn(activity)
            result.onSuccess { idToken ->
                emit(AuthUiState.Loading("Signing in..."))
                emitAll(signInWithGoogle(idToken))
            }
            result.onFailure {
                emit(AuthUiState.Error(it.message ?: "Auth failed"))
            }
        }.onFailure {
            emit(AuthUiState.Error(it.message ?: "Auth failed"))
        }
    }

    fun signInWithGoogle(idToken: String): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Checking credentials..."))
        val result = authRepository.signInWithGoogle(idToken)
        emit(result.fold(
            onSuccess = { AuthUiState.Authenticated(it) },
            onFailure = { AuthUiState.Error(it.message ?: "Auth failed") }
        ))
    }

    fun signOut(): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Signing out..."))
        authRepository.signOut()
        emit(AuthUiState.Unauthenticated)
    }

    fun deleteAccount(): Flow<AuthUiState> = flow {
        emit(AuthUiState.Loading("Deleting Account..."))
        authRepository.deleteAccount().fold(
            onSuccess = { emit(AuthUiState.Unauthenticated) },
            onFailure = { emit(AuthUiState.Error(it.message ?: "Failed to delete account")) }
        )
    }
}