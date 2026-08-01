package com.xs.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.utils.states.AuthUiState

/**
 * Waits only for Firebase to report whether a session exists, then continues regardless of the
 * answer.
 *
 * Auth state no longer decides *where* the user goes — both signed in and signed out land on
 * the tracker list — but it is still worth the brief wait, so the first frame after splash
 * already knows whether to show the account as connected.
 */
@Composable
fun SplashScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authState: AuthUiState,
    onReady: () -> Unit
) {
    LaunchedEffect(authState) {
        if (authState !is AuthUiState.Loading) onReady()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bgDark),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = accentPurple)
    }
}
