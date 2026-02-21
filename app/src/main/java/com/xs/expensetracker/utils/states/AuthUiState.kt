package com.xs.expensetracker.utils.states

import com.google.firebase.auth.FirebaseUser

sealed interface AuthUiState {

    data class Loading(val message: String) : AuthUiState

    object Unauthenticated : AuthUiState

    data class Authenticated(
        val user: FirebaseUser
    ) : AuthUiState

    data class Error(
        val message: String
    ) : AuthUiState
}