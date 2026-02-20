package com.xs.expensetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.utils.AuthUiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    onLogout: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()

    val user = (state as? AuthUiState.Authenticated)?.user

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Welcome ${user?.displayName ?: ""}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(text = user?.email ?: "")

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                authViewModel.signOut()
                onLogout()
            }
        ) {
            Text("Logout")
        }
    }
}