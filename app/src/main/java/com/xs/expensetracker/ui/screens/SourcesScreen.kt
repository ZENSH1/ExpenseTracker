package com.xs.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionSource
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.SharedKeys
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    // -- new params --
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    //
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    type: TransactionType,
    onBack: () -> Unit,
    trackerId: String
) {
    val txState by transactionsViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    var filterType by remember { mutableStateOf<TransactionType?>(type) }
    var showAddModal by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<TransactionSource?>(null) }
    var deletingSource by remember { mutableStateOf<TransactionSource?>(null) }

    LaunchedEffect(trackerId, filterType) {
        transactionsViewModel.observeSources(trackerId, filterType)
    }

    val activeColor = if (filterType == TransactionType.INCOME) incomeColor else expenseColor
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    // Delete confirmation dialog
    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            containerColor = bgCard,
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = { Text("Delete Source?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${source.name}\" and all its data will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    transactionsViewModel.deleteSource(trackerId, source.id)
                    deletingSource = null
                }) { Text("Delete", color = expenseColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    if (showAddModal) {
        SourceFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = filterType ?: type,
            onDismiss = {
                transactionsViewModel.clearError()
                showAddModal = false
            }
        )
    }

    editingSource?.let { source ->
        SourceFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = source.type,
            editingSource = source,
            onDismiss = {
                transactionsViewModel.clearError()
                editingSource = null
            }
        )
    }

    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxSize().background(bgDark)) {
            // Ambient glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.7f
                    ),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.5f, 0f)
                )
            }

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Sources",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textPrimary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showAddModal = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeColor.copy(alpha = 0.15f))
                                    .border(
                                        1.dp,
                                        activeColor.copy(alpha = 0.3f),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add",
                                    tint = activeColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {


                    // ── Summary Card ────────────────────────────────────
                    // When "All" is selected: net = income sources total − expense sources total
                    // When a specific type is selected: show that type's total directly
                    val total = when (filterType) {
                        TransactionType.INCOME  -> txState.sources.sumOf { it.totalAmount }
                        TransactionType.EXPENSE -> txState.sources.sumOf { it.totalAmount }
                        null -> txState.sources.sumOf { source ->
                            if (source.type == TransactionType.INCOME) source.totalAmount
                            else -source.totalAmount
                        }
                    }
                    val isNetPositive = total >= 0.0
                    val summaryColor = when (filterType) {
                        null -> if (isNetPositive) incomeColor else expenseColor
                        else -> activeColor
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 👇 Matches the card bounds from HomeScreen
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(SharedKeys.GRAND_TOTAL_CARD),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgCard)
                            .border(1.dp, summaryColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // 👇 Label flies in from HomeScreen's label
                                Text(
                                    text = when (filterType) {
                                        null -> if (isNetPositive) "Net Balance" else "Net Balance"
                                        else -> "Total ${filterType?.name?.lowercase()?.replaceFirstChar { it.uppercase() }}"
                                    },
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(SharedKeys.GRAND_TOTAL_LABEL),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                                // 👇 Amount flies in from HomeScreen's amount
                                Text(
                                    text = buildString {
                                        if (filterType == null && !isNetPositive) append("−")
                                        append(currency.format(if (filterType == null) kotlin.math.abs(total) else total))
                                    },
                                    color = summaryColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(SharedKeys.GRAND_TOTAL_AMOUNT),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${txState.sources.size} sources",
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(summaryColor.copy(alpha = 0.1f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when (filterType) {
                                            TransactionType.INCOME  -> "↑ Income"
                                            TransactionType.EXPENSE -> "↓ Expense"
                                            null -> if (isNetPositive) "↑ Positive" else "↓ Negative"
                                        },
                                        color = summaryColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // ── Type Filter ─────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(SharedKeys.TABS_LAYOUT),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCard)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(null, TransactionType.INCOME, TransactionType.EXPENSE).forEach { t ->
                            val label =
                                t?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All"
                            val tabColor = when (t) {
                                TransactionType.INCOME -> incomeColor
                                TransactionType.EXPENSE -> expenseColor
                                null -> accentPurple
                            }
                            val isSelected = filterType == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) tabColor.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { filterType = t }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) tabColor else textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ── List ────────────────────────────────────────────
                    if (txState.sources.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("💸", fontSize = 48.sp)
                                Text(
                                    "No sources yet",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Tap + to add your first source",
                                    color = textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(txState.sources, key = { it.id }) { source ->
                            val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(
                                visibleState = visibleState,
                                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    initialOffsetY = { it / 2 }
                                ),
                                exit = fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
                                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -it }
                                ),
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(300),
                                    fadeOutSpec = tween(200),
                                    placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            ) {
                                SourceCard(
                                    source = source,
                                    currency = currency,
                                    onEdit = { editingSource = source },
                                    onDelete = { deletingSource = source }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: TransactionSource,
    currency: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = if (source.type == TransactionType.INCOME) incomeColor else expenseColor
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgCard)
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (source.type == TransactionType.INCOME) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }

        // Info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(source.name, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        source.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Amount + menu
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = currency.format(source.totalAmount),
                color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(bgCard)
                ) {
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
private fun SourceFormModal(
    trackerId: String,
    transactionsViewModel: TransactionsViewModel,
    initialType: TransactionType,
    editingSource: TransactionSource? = null,
    onDismiss: () -> Unit
) {
    val txState by transactionsViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(true)
    val isEditing = editingSource != null

    var name by remember { mutableStateOf(editingSource?.name ?: "") }
    var selectedType by remember { mutableStateOf(editingSource?.type ?: initialType) }
    val activeColor = if (selectedType == TransactionType.INCOME) incomeColor else expenseColor

    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(txState.isLoading) {
        if (wasLoading && !txState.isLoading && txState.error == null) onDismiss()
        wasLoading = txState.isLoading
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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            // Type toggle
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgCard).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TransactionType.values().forEach { t ->
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
                                if (t == TransactionType.INCOME) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
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
                        if (isEditing) {
                            transactionsViewModel.updateReceipt(trackerId, editingSource!!.id,
                                com.xs.expensetracker.data.models.TransactionReceipt()) // placeholder — see note
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