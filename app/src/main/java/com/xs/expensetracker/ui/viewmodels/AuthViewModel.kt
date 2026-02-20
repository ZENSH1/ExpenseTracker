package com.xs.expensetracker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.repo.AuthRepository
import com.xs.expensetracker.utils.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
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

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepository.signInWithGoogle(idToken)

            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Authenticated(it) },
                onFailure = { AuthUiState.Error(it.message ?: "Auth failed") }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}