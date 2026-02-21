package com.xs.expensetracker.ui.components.reusables

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.ui.theme.textSecondary

@Composable
fun SectionLabel(text: String, modifier: Modifier= Modifier) {
    Text(
        modifier = modifier,
        text = text,
        color = textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp
    )
}