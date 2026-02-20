package com.xs.expensetracker.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    type: TransactionType,
    onBack: () -> Unit
) {
    val txState by transactionsViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val trackerId = (authState as? AuthUiState.Authenticated)?.user?.uid ?: return

    var filterSourceId by remember { mutableStateOf<String?>(null) }
    var filterType by remember { mutableStateOf<TransactionType?>(type) }
    var showAddModal by remember { mutableStateOf(false) }
    var editingReceipt by remember { mutableStateOf<TransactionReceipt?>(null) }
    var deletingReceipt by remember { mutableStateOf<TransactionReceipt?>(null) }

    LaunchedEffect(trackerId, filterSourceId) {
        transactionsViewModel.observeSources(trackerId, null) // load all sources for filter chips
        transactionsViewModel.observeReceipts(trackerId, filterSourceId ?: "")
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    val filteredReceipts = txState.receipts.filter { receipt ->
        filterType == null || receipt.type == filterType
    }

    val activeColor = if (filterType == TransactionType.INCOME) incomeColor else if (filterType == TransactionType.EXPENSE) expenseColor else accentPurple

    // Delete dialog
    deletingReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { deletingReceipt = null },
            containerColor = bgCard,
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = { Text("Delete Receipt?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${receipt.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    transactionsViewModel.deleteReceipt(trackerId, receipt.sourceId, receipt.id)
                    deletingReceipt = null
                }) { Text("Delete", color = expenseColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingReceipt = null }) { Text("Cancel", color = textSecondary) }
            }
        )
    }

    if (showAddModal) {
        ReceiptFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = filterType ?: type,
            onDismiss = { transactionsViewModel.clearError(); showAddModal = false }
        )
    }

    editingReceipt?.let { receipt ->
        ReceiptFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = receipt.type,
            editingReceipt = receipt,
            onDismiss = { transactionsViewModel.clearError(); editingReceipt = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(bgDark)) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(activeColor.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f, center = Offset(size.width * 0.5f, 0f)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Receipts", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddModal = true },
                            modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(10.dp))
                                .background(activeColor.copy(alpha = 0.15f))
                                .border(1.dp, activeColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = activeColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Summary ─────────────────────────────────────────
                val total = filteredReceipts.sumOf { it.amount }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(bgCard).border(1.dp, activeColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(20.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total Amount", color = textSecondary, fontSize = 12.sp)
                            Text(currency.format(total), color = activeColor, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("${filteredReceipts.size} receipts", color = textSecondary, fontSize = 12.sp)
                    }
                }

                // ── Type Filter ─────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgCard).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(null, TransactionType.INCOME, TransactionType.EXPENSE).forEach { t ->
                        val label = t?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All"
                        val tabColor = when (t) {
                            TransactionType.INCOME -> incomeColor
                            TransactionType.EXPENSE -> expenseColor
                            null -> accentPurple
                        }
                        val isSelected = filterType == t
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(if (isSelected) 1.dp else 0.dp, if (isSelected) tabColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { filterType = t }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSelected) tabColor else textSecondary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }

                // ── Source Filter Chips ─────────────────────────────
                if (txState.sources.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = filterSourceId == null,
                                onClick = { filterSourceId = null },
                                label = { Text("All Sources", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentPurple.copy(alpha = 0.2f),
                                    selectedLabelColor = accentPurple,
                                    containerColor = bgCard,
                                    labelColor = textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filterSourceId == null,
                                    selectedBorderColor = accentPurple.copy(alpha = 0.4f),
                                    borderColor = textSecondary.copy(alpha = 0.2f)
                                )
                            )
                        }
                        items(txState.sources) { source ->
                            val chipColor = if (source.type == TransactionType.INCOME) incomeColor else expenseColor
                            FilterChip(
                                selected = filterSourceId == source.id,
                                onClick = { filterSourceId = if (filterSourceId == source.id) null else source.id },
                                label = { Text(source.name, fontSize = 12.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.15f),
                                    selectedLabelColor = chipColor,
                                    containerColor = bgCard,
                                    labelColor = textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filterSourceId == source.id,
                                    selectedBorderColor = chipColor.copy(alpha = 0.4f),
                                    borderColor = textSecondary.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                // ── Receipt List ────────────────────────────────────
                if (filteredReceipts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🧾", fontSize = 48.sp)
                            Text("No receipts found", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tap + to record a transaction", color = textSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(filteredReceipts, key = { it.id }) { receipt ->
                            ReceiptCard(
                                receipt = receipt,
                                sourceName = txState.sources.find { it.id == receipt.sourceId }?.name ?: "Unknown",
                                currency = currency,
                                dateFormatter = dateFormatter,
                                onEdit = { editingReceipt = receipt },
                                onDelete = { deletingReceipt = receipt }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptCard(
    receipt: TransactionReceipt,
    sourceName: String,
    currency: NumberFormat,
    dateFormatter: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = if (receipt.type == TransactionType.INCOME) incomeColor else expenseColor
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgCard)
            .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date badge
        Column(
            modifier = Modifier.width(40.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val parts = dateFormatter.format(Date(receipt.date)).split(" ")
            Text(parts.getOrNull(0) ?: "", color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(parts.getOrNull(1) ?: "", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }

        // Info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(receipt.name, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sourceName, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (receipt.description.isNotBlank()) {
                Text(receipt.description, color = textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Amount + menu
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${if (receipt.type == TransactionType.INCOME) "+" else "-"}${currency.format(receipt.amount)}",
                color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, modifier = Modifier.background(bgCard)) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = textPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = accentPurple, modifier = Modifier.size(16.dp)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = expenseColor, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = expenseColor, modifier = Modifier.size(16.dp)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptFormModal(
    trackerId: String,
    transactionsViewModel: TransactionsViewModel,
    initialType: TransactionType,
    editingReceipt: TransactionReceipt? = null,
    onDismiss: () -> Unit
) {
    val txState by transactionsViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(true)
    val isEditing = editingReceipt != null
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    var name by remember { mutableStateOf(editingReceipt?.name ?: "") }
    var description by remember { mutableStateOf(editingReceipt?.description ?: "") }
    var amountText by remember { mutableStateOf(editingReceipt?.amount?.toString() ?: "") }
    var selectedSourceId by remember { mutableStateOf(editingReceipt?.sourceId ?: "") }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(editingReceipt?.date ?: System.currentTimeMillis()) }

    val selectedSource = txState.sources.find { it.id == selectedSourceId }
    val activeColor = if (selectedSource?.type == TransactionType.INCOME) incomeColor else expenseColor

    LaunchedEffect(txState.sources) {
        if (selectedSourceId.isEmpty() && txState.sources.isNotEmpty()) {
            selectedSourceId = txState.sources.first().id
        }
    }

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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = textSecondary) } },
            colors = DatePickerDefaults.colors(containerColor = bgCard)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = bgCard, titleContentColor = textPrimary, headlineContentColor = accentPurple,
                    weekdayContentColor = textSecondary, subheadContentColor = textSecondary,
                    navigationContentColor = textPrimary, yearContentColor = textPrimary,
                    currentYearContentColor = accentPurple, selectedYearContainerColor = accentPurple,
                    dayContentColor = textPrimary, selectedDayContainerColor = accentPurple,
                    todayContentColor = accentPurple, todayDateBorderColor = accentPurple
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgDark,
        dragHandle = {
            Box(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).width(40.dp).height(4.dp).clip(CircleShape).background(textSecondary.copy(alpha = 0.4f)))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isEditing) "Edit Receipt" else "New Receipt", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (isEditing) "Update transaction details" else "Record a transaction", color = textSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = textSecondary) }
            }

            // Source picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Source", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                if (txState.sources.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgCard).border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text("No sources yet. Create a source first.", color = expenseColor, fontSize = 13.sp)
                    }
                } else {
                    ExposedDropdownMenuBox(expanded = sourceDropdownExpanded, onExpandedChange = { sourceDropdownExpanded = it }) {
                        OutlinedTextField(
                            value = selectedSource?.name ?: "Select a source",
                            onValueChange = {}, readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                                focusedBorderColor = accentPurple.copy(alpha = 0.6f), unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                                focusedContainerColor = bgCard, unfocusedContainerColor = bgCard,
                                focusedTrailingIconColor = textSecondary, unfocusedTrailingIconColor = textSecondary
                            )
                        )
                        ExposedDropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }, modifier = Modifier.background(bgCard)) {
                            txState.sources.forEach { source ->
                                val chipColor = if (source.type == TransactionType.INCOME) incomeColor else expenseColor
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(chipColor))
                                            Text(source.name, color = textPrimary, fontSize = 14.sp)
                                            Spacer(Modifier.weight(1f))
                                            Text(source.type.name.lowercase().replaceFirstChar { it.uppercase() }, color = chipColor, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = { selectedSourceId = source.id; sourceDropdownExpanded = false },
                                    modifier = Modifier.background(if (source.id == selectedSourceId) chipColor.copy(alpha = 0.08f) else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            // Name
            ReceiptField("Receipt Name", name, { name = it }, "e.g. Netflix, Rent...", activeColor, KeyboardCapitalization.Words)

            // Description
            ReceiptField("Description (optional)", description, { description = it }, "Add a note...", activeColor, singleLine = false)

            // Amount
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Amount", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.00", color = textSecondary.copy(alpha = 0.5f)) },
                    leadingIcon = { Text("$", color = textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                        focusedBorderColor = activeColor.copy(alpha = 0.6f), unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                        cursorColor = activeColor, focusedContainerColor = bgCard, unfocusedContainerColor = bgCard
                    )
                )
            }

            // Date
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = dateFormatter.format(Date(selectedDateMillis)),
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp), enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = textPrimary,
                        disabledBorderColor = textSecondary.copy(alpha = 0.2f),
                        disabledContainerColor = bgCard, disabledTrailingIconColor = textSecondary
                    )
                )
            }

            txState.error?.let { Text(it, color = expenseColor, fontSize = 12.sp) }

            val isValid = name.isNotBlank() && selectedSourceId.isNotEmpty() && (amountText.toDoubleOrNull() ?: 0.0) > 0

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    if (isEditing) {
                        transactionsViewModel.updateReceipt(
                            trackerId, selectedSourceId,
                            editingReceipt!!.copy(name = name.trim(), description = description.trim(), amount = amount, date = selectedDateMillis, sourceId = selectedSourceId)
                        )
                    } else {
                        transactionsViewModel.addReceipt(trackerId, selectedSourceId, name.trim(), description.trim(), amount, selectedDateMillis)
                    }
                },
                enabled = isValid && !txState.isLoading,
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
                    Icon(if (isEditing) Icons.Filled.Check else Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditing) "Save Changes" else "Save Receipt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ReceiptField(
    label: String, value: String, onValueChange: (String) -> Unit,
    placeholder: String, activeColor: Color,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = textSecondary.copy(alpha = 0.5f)) },
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(capitalization = capitalization),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                focusedBorderColor = activeColor.copy(alpha = 0.6f), unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                cursorColor = activeColor, focusedContainerColor = bgCard, unfocusedContainerColor = bgCard
            )
        )
    }
}