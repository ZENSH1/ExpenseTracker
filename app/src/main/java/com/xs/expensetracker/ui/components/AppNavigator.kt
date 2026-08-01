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
import com.xs.expensetracker.ui.components.modals.AccountChangeDialog
import com.xs.expensetracker.ui.components.modals.LinkDecisionDialog
import com.xs.expensetracker.ui.screens.AuthScreen
import com.xs.expensetracker.ui.screens.ConflictsScreen
import com.xs.expensetracker.ui.screens.HomeScreen
import com.xs.expensetracker.ui.screens.ProfileScreen
import com.xs.expensetracker.ui.screens.ReceiptsScreen
import com.xs.expensetracker.ui.screens.SettingsScreen
import com.xs.expensetracker.ui.screens.SourcesScreen
import com.xs.expensetracker.ui.screens.SplashScreen
import com.xs.expensetracker.ui.screens.TrackerSelectionScreen
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.SyncViewModel
import com.xs.expensetracker.utils.sealed.AuthRoute
import com.xs.expensetracker.utils.sealed.ConflictsRoute
import com.xs.expensetracker.utils.sealed.HomeRoute
import com.xs.expensetracker.utils.sealed.ProfileRoute
import com.xs.expensetracker.utils.sealed.ReceiptsRoute
import com.xs.expensetracker.utils.sealed.SettingsRoute
import com.xs.expensetracker.utils.sealed.SourcesRoute
import com.xs.expensetracker.utils.sealed.SplashRoute
import com.xs.expensetracker.utils.sealed.TrackerSelectionRoute
import org.koin.androidx.compose.koinViewModel

/**
 * Root navigation.
 *
 * Auth is no longer a gate. Splash goes straight to the tracker list whether or not anyone is
 * signed in, and signing out returns there rather than bouncing to a login wall — the local
 * database is still there and still fully usable.
 *
 * The two sync decision dialogs are hosted here rather than on a screen, because they can be
 * raised by a background worker at any moment and must be answerable from wherever the user
 * happens to be.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigator(
    authViewModel: AuthViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()
    val backStack = rememberNavBackStack(SplashRoute)
    val currentKey = backStack.lastOrNull() ?: SplashRoute

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    fun resetTo(route: androidx.navigation3.runtime.NavKey) {
        backStack.clear()
        backStack.add(route)
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
                    onReady = { resetTo(TrackerSelectionRoute) }
                )

                // ── Auth (optional, only ever reached deliberately) ──────
                AuthRoute -> AuthScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onLoginSuccess = { resetTo(TrackerSelectionRoute) },
                    onSkip = { resetTo(TrackerSelectionRoute) }
                )

                // ── Tracker Selection ─────────────────────────────────────
                TrackerSelectionRoute -> TrackerSelectionScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onTrackerSelected = { tracker -> backStack.add(HomeRoute(tracker.id)) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    onOpenProfile = { backStack.add(ProfileRoute) },
                    onOpenConflicts = { backStack.add(ConflictsRoute) }
                )

                // ── Home ─────────────────────────────────────────────────
                is HomeRoute -> HomeScreen(
                    trackerId = key.trackerId,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onNavigateToSources = { type ->
                        backStack.add(SourcesRoute(trackerId = key.trackerId, type = type))
                    },
                    onNavigateToReceipts = { type ->
                        backStack.add(ReceiptsRoute(trackerId = key.trackerId, type = type))
                    },
                    onProfileClicked = { backStack.add(ProfileRoute) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    onBack = { backStack.removeLastOrNull() }
                )

                // ── Profile ──────────────────────────────────────────────
                ProfileRoute -> ProfileScreen(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    // Signing out keeps local data, so there is nowhere to eject the user to.
                    onSignedOut = { resetTo(TrackerSelectionRoute) },
                    onSignIn = { backStack.add(AuthRoute) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    onBack = { backStack.removeLastOrNull() }
                )

                // ── Settings ─────────────────────────────────────────────
                SettingsRoute -> SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenConflicts = { backStack.add(ConflictsRoute) }
                )

                // ── Conflicts ────────────────────────────────────────────
                ConflictsRoute -> ConflictsScreen(
                    onBack = { backStack.removeLastOrNull() }
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

    // ── Sync decisions ───────────────────────────────────────────────────────
    // Hosted above the back stack: a background sync can raise either of these while the user
    // is anywhere in the app, and both must be answered before syncing can continue.

    val pendingAccount = syncState.pendingAccountUid
    val pendingLink = syncState.pendingLink

    when {
        // An account switch is settled first: until it is, "whose data is this?" is unanswered,
        // which makes any link decision meaningless.
        pendingAccount != null -> AccountChangeDialog(
            localRecords = syncState.localRecordCount,
            onChoose = syncViewModel::resolveAccountChange
        )

        pendingLink != null -> LinkDecisionDialog(
            localRecords = pendingLink.localRecords,
            remoteTrackers = pendingLink.remoteTrackers,
            onChoose = syncViewModel::resolveLinkDecision
        )
    }
}
