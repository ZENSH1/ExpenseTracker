package com.xs.expensetracker.ui.components.modals

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
import com.xs.expensetracker.ui.components.reusables.ModalTextField
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptModal(
    trackerId: String,
    transactionsViewModel: TransactionsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(true)
    val txState by transactionsViewModel.uiState.collectAsState()


    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedSourceId by remember { mutableStateOf("") }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val selectedSource = txState.sources.find { it.id == selectedSourceId }
    val activeColor = if (selectedSource?.type == TransactionType.INCOME) incomeColor else expenseColor

    // Auto-select first source if available
    LaunchedEffect(txState.sources) {
        if (selectedSourceId.isEmpty() && txState.sources.isNotEmpty()) {
            selectedSourceId = txState.sources.first().id
        }
    }

    // Dismiss on success
    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(txState.isLoading) {
        if (wasLoading && !txState.isLoading && txState.error == null) onDismiss()
        wasLoading = txState.isLoading
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK", color = accentPurple) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = textSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = bgCard)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = bgCard,
                    titleContentColor = textPrimary,
                    headlineContentColor = accentPurple,
                    weekdayContentColor = textSecondary,
                    subheadContentColor = textSecondary,
                    navigationContentColor = textPrimary,
                    yearContentColor = textPrimary,
                    currentYearContentColor = accentPurple,
                    selectedYearContainerColor = accentPurple,
                    dayContentColor = textPrimary,
                    selectedDayContainerColor = accentPurple,
                    todayContentColor = accentPurple,
                    todayDateBorderColor = accentPurple
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(textSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("New Receipt", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Record a transaction", color = textSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            // Source picker dropdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Source", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)

                if (txState.sources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCard)
                            .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text("No sources yet. Create a source first.", color = expenseColor, fontSize = 13.sp)
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = sourceDropdownExpanded,
                        onExpandedChange = { sourceDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSource?.name ?: "Select a source",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedBorderColor = accentPurple.copy(alpha = 0.6f),
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                                focusedContainerColor = bgCard,
                                unfocusedContainerColor = bgCard,
                                focusedTrailingIconColor = textSecondary,
                                unfocusedTrailingIconColor = textSecondary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = sourceDropdownExpanded,
                            onDismissRequest = { sourceDropdownExpanded = false },
                            modifier = Modifier.background(bgCard)
                        ) {
                            txState.sources.forEach { source ->
                                val itemColor = if (source.type == TransactionType.INCOME) incomeColor else expenseColor
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(itemColor)
                                            )
                                            Text(source.name, color = textPrimary, fontSize = 14.sp)
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                source.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                                color = itemColor,
                                                fontSize = 11.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSourceId = source.id
                                        sourceDropdownExpanded = false
                                    },
                                    modifier = Modifier.background(
                                        if (source.id == selectedSourceId) itemColor.copy(alpha = 0.08f) else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Name field
            ModalTextField(
                label = "Receipt Name",
                value = name,
                onValueChange = { name = it },
                placeholder = "e.g. Netflix, Rent...",
                activeColor = activeColor,
                bgCard = bgCard,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                capitalization = KeyboardCapitalization.Words
            )

            // Description field
            ModalTextField(
                label = "Description (optional)",
                value = description,
                onValueChange = { description = it },
                placeholder = "Add a note...",
                activeColor = activeColor,
                bgCard = bgCard,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                singleLine = false
            )

            // Amount field
            ModalTextField(
                label = "Amount",
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder = "0.00",
                activeColor = activeColor,
                bgCard = bgCard,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                keyboardType = KeyboardType.Decimal,
                leadingIcon = {
                    Text("$", color = textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            )

            // Date picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = dateFormatter.format(Date(selectedDateMillis)),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    trailingIcon = {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = activeColor.copy(alpha = 0.6f),
                        unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                        focusedContainerColor = bgCard,
                        unfocusedContainerColor = bgCard
                    ),
                    enabled = false // makes it fully clickable without keyboard
                )
            }

            // Error
            txState.error?.let {
                Text(it, color = expenseColor, fontSize = 12.sp)
            }

            val isValid = name.isNotBlank() &&
                    selectedSourceId.isNotEmpty() &&
                    amountText.toDoubleOrNull() != null &&
                    amountText.toDoubleOrNull()!! > 0

            // Save button
            Button(
                onClick = {
                    transactionsViewModel.addReceipt(
                        trackerId = trackerId,
                        sourceId = selectedSourceId,
                        name = name.trim(),
                        description = description.trim(),
                        amount = amountText.toDoubleOrNull() ?: 0.0,
                        type = selectedSource?.type ?: TransactionType.EXPENSE,
                        date = selectedDateMillis
                    )
                },
                enabled = isValid && !txState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = Color(0xFF0D0D14),
                    disabledContainerColor = activeColor.copy(alpha = 0.3f),
                    disabledContentColor = Color(0xFF0D0D14).copy(alpha = 0.5f)
                )
            ) {
                if (txState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0D0D14), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Receipt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
