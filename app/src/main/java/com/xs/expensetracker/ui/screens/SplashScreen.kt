package com.xs.expensetracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xs.expensetracker.utils.AuthUiState

@Composable
fun SplashScreen(
    authState: AuthUiState,
    onResult: (Boolean) -> Unit
) {
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Authenticated -> onResult(true)
            is AuthUiState.Unauthenticated -> onResult(false)
            else -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}