package com.xs.expensetracker.ui.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xs.expensetracker.usecases.AuthUseCase
import com.xs.expensetracker.utils.states.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authUseCase: AuthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading("Initializing..."))
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authUseCase.observeAuthState().collect { _uiState.value = it }
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            authUseCase.signIn(activity).collect { _uiState.value = it }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authUseCase.signOut().collect { _uiState.value = it }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authUseCase.deleteAccount().collect { _uiState.value = it }
        }
    }
}