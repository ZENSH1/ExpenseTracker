package com.xs.expensetracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.xs.expensetracker.ui.screens.AuthScreen
import com.xs.expensetracker.ui.screens.HomeScreen
import com.xs.expensetracker.ui.screens.SplashScreen
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.utils.sealed.AuthRoute
import com.xs.expensetracker.utils.sealed.HomeRoute
import com.xs.expensetracker.utils.sealed.SplashRoute
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavigator(
    authViewModel: AuthViewModel = koinViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()

    // create backstack
    val backStack = rememberNavBackStack(SplashRoute)

    // create NavEntries for current backstack
    val entries = rememberDecoratedNavEntries(backStack) { key ->
        when (key) {
            SplashRoute -> NavEntry(key) {
                SplashScreen(
                    authState = authState,
                    onResult = { loggedIn ->
                        backStack.clear()
                        backStack.add(if (loggedIn) HomeRoute else AuthRoute)
                    }
                )
            }
            AuthRoute -> NavEntry(key) {
                AuthScreen(
                    onLoginSuccess = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    }
                )
            }
            HomeRoute -> NavEntry(key) {
                HomeScreen(
                    onLogout = {
                        authViewModel.signOut()
                        backStack.clear()
                        backStack.add(AuthRoute)
                    }
                )
            }
            else -> NavEntry(key) {
                Box(Modifier.fillMaxSize()) { Text("Unknown destination") }
            }
        }
    }

    NavDisplay(
        entries = entries,
        onBack = { backStack.removeLastOrNull() }
    )
}