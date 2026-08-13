package com.xs.expensetracker.ui.components.modals

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.*
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.TransactionSource
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.states.ActionTarget
import kotlinx.coroutines.launch

/**
 * Create or edit a transaction source.
 *
 * Shared by the home screen and the sources screen. It used to exist twice — as AddSourceModal
 * and as a private copy inside SourcesScreen — which is how the same dismissal bug came to be
 * written down in two places and fixed in neither.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceFormModal(
    trackerId: String,
    transactionsViewModel: TransactionsViewModel,
    initialType: TransactionType,
    editingSource: TransactionSource? = null,
    onDismiss: () -> Unit
) {
    val txState by transactionsViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(true)
    val scope = rememberCoroutineScope()
    val isEditing = editingSource != null

    var name by remember { mutableStateOf(editingSource?.name ?: "") }
    var selectedType by remember { mutableStateOf(editingSource?.type ?: initialType) }
    val activeColor = if (selectedType == TransactionType.INCOME) incomeColor else expenseColor

    /** Lets the sheet slide out instead of blinking away the instant state changes. */
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    LaunchedEffect(Unit) { transactionsViewModel.clearError() }

    // Close on a save that actually landed, rather than on isLoading falling back to false —
    // a local write often finishes inside one frame, and the UI never observes the rise.
    val resultIdAtOpen = remember { txState.lastResult?.id ?: 0L }
    LaunchedEffect(txState.lastResult) {
        val result = txState.lastResult ?: return@LaunchedEffect
        if (result.id > resultIdAtOpen && result.target == ActionTarget.SOURCE) close()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgDark,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp).height(4.dp).clip(CircleShape).background(textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isEditing) "Edit Source" else "New Source", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (isEditing) "Update source details" else "Track where money comes or goes", color = textSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = { close() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            // Type toggle
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgCard).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TransactionType.entries.forEach { t ->
                    val isSelected = selectedType == t
                    val tabColor = if (t == TransactionType.INCOME) incomeColor else expenseColor
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent)
                            .border(if (isSelected) 1.dp else 0.dp, if (isSelected) tabColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedType = t }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (t == TransactionType.INCOME) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null, tint = if (isSelected) tabColor else textSecondary, modifier = Modifier.size(15.dp)
                            )
                            Text(t.name.lowercase().replaceFirstChar { it.uppercase() }, color = if (isSelected) tabColor else textSecondary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Source Name", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Salary, Groceries...", color = textSecondary.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                        focusedBorderColor = activeColor.copy(alpha = 0.6f), unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                        cursorColor = activeColor, focusedContainerColor = bgCard, unfocusedContainerColor = bgCard
                    )
                )
            }

            txState.error?.let { Text(it, color = expenseColor, fontSize = 12.sp) }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (editingSource != null) {
                            transactionsViewModel.updateSource(
                                sourceId = editingSource.id,
                                name = name.trim(),
                                type = selectedType
                            )
                        } else {
                            transactionsViewModel.createSource(trackerId, name.trim(), selectedType)
                        }
                    }
                },
                enabled = name.isNotBlank() && !txState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor, contentColor = Color(0xFF0D0D14),
                    disabledContainerColor = activeColor.copy(alpha = 0.3f), disabledContentColor = Color(0xFF0D0D14).copy(alpha = 0.5f)
                )
            ) {
                if (txState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0D0D14), strokeWidth = 2.dp)
                } else {
                    Icon(if (isEditing) Icons.Filled.Check else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditing) "Save Changes" else "Create Source", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
