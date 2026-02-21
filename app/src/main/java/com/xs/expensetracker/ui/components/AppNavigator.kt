package com.xs.expensetracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.rememberNavBackStack
import com.xs.expensetracker.ui.screens.AuthScreen
import com.xs.expensetracker.ui.screens.HomeScreen
import com.xs.expensetracker.ui.screens.ProfileScreen
import com.xs.expensetracker.ui.screens.ReceiptsScreen
import com.xs.expensetracker.ui.screens.SourcesScreen
import com.xs.expensetracker.ui.screens.SplashScreen
import com.xs.expensetracker.ui.screens.TrackerSelectionScreen
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.utils.sealed.AuthRoute
import com.xs.expensetracker.utils.sealed.HomeRoute
import com.xs.expensetracker.utils.sealed.ProfileRoute
import com.xs.expensetracker.utils.sealed.ReceiptsRoute
import com.xs.expensetracker.utils.sealed.SourcesRoute
import com.xs.expensetracker.utils.sealed.SplashRoute
import com.xs.expensetracker.utils.sealed.TrackerSelectionRoute
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigator(
    authViewModel: AuthViewModel = koinViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val backStack = rememberNavBackStack(SplashRoute)
    val currentKey = backStack.lastOrNull() ?: SplashRoute

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = currentKey,
            transitionSpec = {
                (slideInHorizontally(tween(350)) { it } + fadeIn(tween(350)))
                    .togetherWith(slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350)))
            },
            label = "root_nav"
        ) { key ->

            when (key) {

                // ── Splash ───────────────────────────────────────────────
                SplashRoute -> SplashScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    authState = authState,
                    onResult = { loggedIn ->
                        backStack.clear()
                        backStack.add(if (loggedIn) TrackerSelectionRoute else AuthRoute)
                    }
                )

                // ── Auth ─────────────────────────────────────────────────
                AuthRoute -> AuthScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(TrackerSelectionRoute)
                    }
                )

                // ── Tracker Selection ─────────────────────────────────────
                TrackerSelectionRoute -> TrackerSelectionScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onTrackerSelected = { tracker ->
                        backStack.add(HomeRoute(tracker))
                    },
                    onLogout = {
                        authViewModel.signOut()
                        backStack.clear()
                        backStack.add(AuthRoute)
                    }
                )

                // ── Home ─────────────────────────────────────────────────
                is HomeRoute -> HomeScreen(
                    tracker = key.tracker,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onLogout = {
                        authViewModel.signOut()
                        backStack.clear()
                        backStack.add(AuthRoute)
                    },
                    onNavigateToSources = { type ->
                        backStack.add(SourcesRoute(trackerId = key.tracker.id, type = type))
                    },
                    onNavigateToReceipts = { type ->
                        backStack.add(ReceiptsRoute(trackerId = key.tracker.id, type = type))
                    },
                    onProfileClicked = {
                        backStack.add(ProfileRoute)
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )

                // ── Profile ──────────────────────────────────────────────
                ProfileRoute -> ProfileScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onLogout = {
                        authViewModel.signOut()
                        backStack.clear()
                        backStack.add(AuthRoute)
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )

                // ── Sources ──────────────────────────────────────────────
                is SourcesRoute -> SourcesScreen(
                    trackerId = key.trackerId,
                    type = key.type,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onBack = { backStack.removeLastOrNull() }
                )

                // ── Receipts ─────────────────────────────────────────────
                is ReceiptsRoute -> ReceiptsScreen(
                    trackerId = key.trackerId,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    type = key.type,
                    onBack = { backStack.removeLastOrNull() }
                )

                else -> Box(Modifier.fillMaxSize()) { Text("Unknown destination") }
            }
        }
    }
}