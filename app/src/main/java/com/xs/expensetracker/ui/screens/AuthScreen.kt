package com.xs.expensetracker.ui.screens

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xs.expensetracker.R
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.utils.AuthUiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    onLoginSuccess: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity ?: return



    if (state is AuthUiState.Authenticated) {
        LaunchedEffect(Unit) { onLoginSuccess() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Icon(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                authViewModel.signIn(activity)
            }
        ) {
            Text("Sign in with Google")
        }

        Spacer(Modifier.height(40.dp))

        when(state){
            is AuthUiState.Authenticated ->
                Text("Authentication", color = Color.Green)
            is AuthUiState.Error ->
                Text("${(state as AuthUiState.Error)}", color = Color.Red)
            AuthUiState.Loading ->
                Text("Loading", color = Color.DarkGray)
            AuthUiState.Unauthenticated ->
                Text("Unauthenticated", color = Color.Yellow)
        }
    }
}