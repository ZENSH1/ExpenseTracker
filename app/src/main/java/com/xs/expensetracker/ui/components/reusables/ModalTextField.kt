package com.xs.expensetracker.ui.components.reusables

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun ModalTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    activeColor: Color,
    bgCard: Color,
    textPrimary: Color,
    textSecondary: Color,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = textSecondary.copy(alpha = 0.5f)) },
            singleLine = singleLine,
            leadingIcon = leadingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                focusedBorderColor = activeColor.copy(alpha = 0.6f),
                unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                cursorColor = activeColor,
                focusedContainerColor = bgCard,
                unfocusedContainerColor = bgCard,
                focusedLeadingIconColor = textSecondary,
                unfocusedLeadingIconColor = textSecondary
            )
        )
    }
}