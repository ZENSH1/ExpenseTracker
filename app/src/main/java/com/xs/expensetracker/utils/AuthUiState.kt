package com.xs.expensetracker.utils

import com.google.firebase.auth.FirebaseUser

sealed interface AuthUiState {

    object Loading : AuthUiState

    object Unauthenticated : AuthUiState

    data class Authenticated(
        val user: FirebaseUser
    ) : AuthUiState

    data class Error(
        val message: String
    ) : AuthUiState
}