package com.xs.expensetracker.ui.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.repo.AuthRepository
import com.xs.expensetracker.utils.states.AuthUiState
import com.xs.expensetracker.utils.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<AuthUiState>(AuthUiState.Loading)

    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.observeAuthState()
                .collect { user ->
                    _uiState.value =
                        if (user != null) {
                            AuthUiState.Authenticated(user)
                        } else {
                            AuthUiState.Unauthenticated
                        }
                }
        }
    }


    fun signIn(activity: Activity){
        viewModelScope.launch {
            runCatching {
                setAuthState(AuthUiState.Loading)
                val result = googleAuthManager.signIn(activity)
                result.onSuccess { text ->
                    signInWithGoogle(text)
                }
                result.onFailure {
                    setAuthState(AuthUiState.Error(it.message ?: "Auth failed"))
                }
            }.onFailure {
                setAuthState(AuthUiState.Error(it.message ?: "Auth failed"))
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            setAuthState(AuthUiState.Loading)
            val result = authRepository.signInWithGoogle(idToken)
            setAuthState(result.fold(
                onSuccess = { AuthUiState.Authenticated(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Auth failed") }
            ))
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    //State Emissions
    fun setAuthState(authState: AuthUiState){
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.emit(authState)
        }
    }
}