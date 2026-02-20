package com.xs.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.xs.expensetracker.data.enums.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    type: TransactionType,
    onBack: () -> Unit
) {
    val bgDark = Color(0xFF0D0D14)
    val textPrimary = Color(0xFFF0F0FF)
    val textSecondary = Color(0xFF8A8AAF)
    val activeColor = if (type == TransactionType.INCOME) Color(0xFF00C9A7) else Color(0xFFFF6B6B)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${type.name.lowercase().replaceFirstChar { it.uppercase() }} Sources",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🚧", fontSize = 48.sp)
                Text("Sources Screen", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Coming soon", color = textSecondary, fontSize = 14.sp)
                Text(
                    type.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = activeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    type: TransactionType,
    onBack: () -> Unit
) {
    val bgDark = Color(0xFF0D0D14)
    val textPrimary = Color(0xFFF0F0FF)
    val textSecondary = Color(0xFF8A8AAF)
    val activeColor = if (type == TransactionType.INCOME) Color(0xFF00C9A7) else Color(0xFFFF6B6B)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${type.name.lowercase().replaceFirstChar { it.uppercase() }} Receipts",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🚧", fontSize = 48.sp)
                Text("Receipts Screen", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Coming soon", color = textSecondary, fontSize = 14.sp)
                Text(
                    type.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = activeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}