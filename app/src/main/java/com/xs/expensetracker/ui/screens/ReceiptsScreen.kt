package com.xs.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.TransactionReceipt
import com.xs.expensetracker.domain.data.models.TransactionSource
import com.xs.expensetracker.ui.components.reusables.ActionResultBar
import com.xs.expensetracker.ui.components.reusables.ReceiptCard
import com.xs.expensetracker.ui.components.reusables.ReceiptFormModal
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.SharedKeys
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    type: TransactionType,
    onBack: () -> Unit,
    trackerId: String,
) {
    val txState by transactionsViewModel.uiState.collectAsState()

    fun getSourceById(sourceId: String): TransactionSource? =
        txState.sources.findLast { it.id == sourceId }

    // null = All Sources; non-null = specific source
    var filterSourceId by remember { mutableStateOf<String?>(null) }
    var filterType by remember { mutableStateOf<TransactionType?>(type) }
    var showAddModal by remember { mutableStateOf(false) }
    var editingReceipt by remember { mutableStateOf<TransactionReceipt?>(null) }
    var deletingReceipt by remember { mutableStateOf<TransactionReceipt?>(null) }

    // Re-observe whenever trackerId or filterSourceId changes.
    // filterSourceId = null → all receipts across the tracker (collection group query).
    LaunchedEffect(trackerId, filterSourceId) {
        transactionsViewModel.observeSources(trackerId, null) // load all sources for filter chips
        transactionsViewModel.observeReceipts(trackerId, filterSourceId) // null = all
    }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    val filteredReceipts = txState.receipts.filter { receipt ->
        filterType == null || receipt.type == filterType || getSourceById(receipt.sourceId)?.type == filterType
    }

    val activeColor = if (filterType == TransactionType.INCOME) incomeColor
    else if (filterType == TransactionType.EXPENSE) expenseColor
    else accentPurple

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
                    transactionsViewModel.deleteReceipt(receipt.id)
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

    with(sharedTransitionScope) {
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
                        title = {
                            Text(
                                "Receipts",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textPrimary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showAddModal = true },
                                modifier = Modifier.padding(end = 8.dp)
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
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // ── Summary ─────────────────────────────────────────
                    // For "All" type: income receipts add, expense receipts subtract (net balance)
                    val total = when (filterType) {
                        null -> filteredReceipts.sumOf { receipt ->
                            if (receipt.type == TransactionType.INCOME) receipt.amount else -receipt.amount
                        }
                        else -> filteredReceipts.sumOf { it.amount }
                    }
                    val isNetPositive = total >= 0.0
                    val summaryColor = when (filterType) {
                        null -> if (isNetPositive) incomeColor else expenseColor
                        else -> activeColor
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState("${SharedKeys.TRACKER_CARD}${trackerId}"),
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
                                Text(
                                    text = if (filterType == null) "Net Balance" else "Total Amount",
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = buildString {
                                        if (filterType == null && !isNetPositive) append("−")
                                        append(currency.format(if (filterType == null) kotlin.math.abs(total) else total))
                                    },
                                    color = summaryColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                "${filteredReceipts.size} receipts",
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // ── Type Filter ─────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(SharedKeys.TABS_LAYOUT),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCard).padding(4.dp),
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
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(
                                        if (isSelected) 1.dp else 0.dp,
                                        if (isSelected) tabColor.copy(alpha = 0.5f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { filterType = t }.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) tabColor else textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ── Source Filter Chips ─────────────────────────────
                    if (txState.sources.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(SharedKeys.ROW_ITEMS),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                            items(
                                txState.sources.filter { it.type == filterType || filterType == null },
                                key = { it.id }
                            ) { source ->
                                val chipColor =
                                    if (source.type == TransactionType.INCOME) incomeColor else expenseColor

                                val visibleState =
                                    remember { MutableTransitionState(false).apply { targetState = true } }

                                AnimatedVisibility(
                                    visibleState = visibleState,
                                    enter = fadeIn(tween(300)) + scaleIn(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        initialScale = 0.7f
                                    ) + expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ),
                                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.7f) + shrinkHorizontally(
                                        animationSpec = tween(200)
                                    ),
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(300),
                                        fadeOutSpec = tween(200),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                ) {
                                    FilterChip(
                                        selected = filterSourceId == source.id,
                                        onClick = {
                                            filterSourceId =
                                                if (filterSourceId == source.id) null else source.id
                                        },
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
                    }

                    // ── Receipt List ────────────────────────────────────
                    if (filteredReceipts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("🧾", fontSize = 48.sp)
                                Text(
                                    "No receipts found",
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Tap + to record a transaction",
                                    color = textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(filteredReceipts, key = { it.id }) { receipt ->
                                val visibleState = remember {
                                    MutableTransitionState(false).apply { targetState = true }
                                }
                                AnimatedVisibility(
                                    visibleState = visibleState,
                                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        initialOffsetY = { it / 2 }
                                    ),
                                    exit = fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                        targetOffsetX = { -it }
                                    ),
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(300),
                                        fadeOutSpec = tween(200),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                ) {
                                    ReceiptCard(
                                        receipt = receipt,
                                        sourceName = txState.sources.find { it.id == receipt.sourceId }?.name
                                            ?: "Unknown",
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

            // ── Floating Loading Indicator ───────────────────────
            AnimatedVisibility(
                visible = txState.isLoading,
                enter = fadeIn(tween(300)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(bgCard)
                        .border(1.dp, activeColor.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = activeColor
                        )
                        Text("Loading...", color = textSecondary, fontSize = 13.sp)
                    }
                }
            }

            // ── Floating Error Snackbar ──────────────────────────
            AnimatedVisibility(
                visible = txState.error != null,
                enter = fadeIn(tween(300)) + slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(bgCard)
                        .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                        .clickable { transactionsViewModel.clearError() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = expenseColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = txState.error ?: "",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // ── Save confirmation ────────────────────────────────
            ActionResultBar(
                result = txState.lastResult,
                onConsume = transactionsViewModel::consumeResult,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }
}