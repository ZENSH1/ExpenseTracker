package com.xs.expensetracker.utils

import androidx.compose.runtime.Composable
import com.xs.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun PreviewScreens(screen: @Composable () -> Unit) {
    ExpenseTrackerTheme {
        screen()
    }
}