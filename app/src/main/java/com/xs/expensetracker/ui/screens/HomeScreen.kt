package com.xs.expensetracker.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.xs.expensetracker.domain.data.enums.TransactionType
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.ui.components.modals.SourceFormModal
import com.xs.expensetracker.ui.components.reusables.ActionResultBar
import com.xs.expensetracker.ui.components.reusables.NavCard
import com.xs.expensetracker.ui.components.reusables.QuickActionButton
import com.xs.expensetracker.ui.components.reusables.ReceiptFormModal
import com.xs.expensetracker.ui.components.reusables.SyncStatusIndicator
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.SyncViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.SharedKeys
import com.xs.expensetracker.utils.states.AuthUiState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    trackerId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel(),
    onNavigateToSources: (TransactionType) -> Unit,
    onNavigateToReceipts: (TransactionType) -> Unit,
    onProfileClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val txState   by transactionsViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()

    val user = (authState as? AuthUiState.Authenticated)?.user

    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // The tracker is observed rather than carried in the route, so background syncs and edits
    // on other screens are reflected here without a round trip through navigation.
    val tracker = txState.selectedTracker

    var showAddSourceModal  by remember { mutableStateOf(false) }
    var showAddReceiptModal by remember { mutableStateOf(false) }
    var selectedType        by remember { mutableStateOf(TransactionType.INCOME) }

    // ── Export state ─────────────────────────────────────────────────────────
    var isExporting         by remember { mutableStateOf(false) }
    var exportSnackbarMsg   by remember { mutableStateOf<String?>(null) }

    // helper: fire share intent for a FileProvider URI
    fun shareUri(uri: android.net.Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun onExportCsv() {
        val current = tracker ?: return
        if (isExporting) return
        coroutineScope.launch {
            isExporting = true
            val uri = transactionsViewModel.exportToCsv(context, current)
            isExporting = false
            if (uri != null) {
                shareUri(uri, "text/csv", "Share CSV Report")
            } else {
                exportSnackbarMsg = "CSV export failed. Please try again."
            }
        }
    }

    fun onExportPdf() {
        val current = tracker ?: return
        if (isExporting) return
        coroutineScope.launch {
            isExporting = true
            val uri = transactionsViewModel.exportToPdf(context, current)
            isExporting = false
            if (uri != null) {
                shareUri(uri, "application/pdf", "Share PDF Report")
            } else {
                exportSnackbarMsg = "PDF export failed. Please try again."
            }
        }
    }

    val glowColor by animateColorAsState(
        targetValue = if (selectedType == TransactionType.INCOME)
            Color(0xFF00C9A7).copy(alpha = 0.12f)
        else
            Color(0xFFFF6B6B).copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 600, easing = EaseInOutCubic),
        label = "glowColor"
    )

    val activeColor by animateColorAsState(
        targetValue = if (selectedType == TransactionType.INCOME)
            Color(0xFF00C9A7)
        else
            Color(0xFFFF6B6B),
        animationSpec = tween(durationMillis = 600, easing = EaseInOutCubic),
        label = "activeColor"
    )

    // Split by key on purpose: only the source list depends on the selected tab, so flipping
    // between Income and Expense no longer tears down and restarts the tracker and receipt
    // observers along with it.
    LaunchedEffect(trackerId) {
        transactionsViewModel.observeTracker(trackerId)
        transactionsViewModel.observeReceipts(trackerId, null)
    }

    LaunchedEffect(trackerId, selectedType) {
        transactionsViewModel.observeSources(trackerId, selectedType)
    }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.15f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.5f, size.height * 0.15f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Top Bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Column(Modifier.clickable { onProfileClicked() }) {
                            Text(
                                text = user?.displayName ?: "Local only",
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                modifier = Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(SharedKeys.USER_PROFILE_NAME),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                ),
                                text = tracker?.name ?: "Tracker",
                                color = textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(SharedKeys.USER_PROFILE_IMAGE),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                )
                                .clip(CircleShape)
                                .background(accentPurple.copy(alpha = 0.2f))
                                .clickable { onProfileClicked() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                color = accentPurple,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        SyncStatusIndicator(status = syncState.status, onClick = onOpenSettings)

                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Sync & settings",
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ── Grand Total Card ──────────────────────────────────────
                GrandTotalCard(
                    Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState("${SharedKeys.TRACKER_CARD}$trackerId"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    grandTotal = tracker?.grandTotal ?: 0.0,
                    currencyFormatter = currencyFormatter,
                    isExporting = isExporting,
                    onExportCsv = { onExportCsv() },
                    onExportPdf = { onExportPdf() }
                )

                // ── Income / Expense Tabs ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(SharedKeys.TABS_LAYOUT),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgCard)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransactionType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        val tabColor by animateColorAsState(
                            targetValue = if (type == TransactionType.INCOME) incomeColor else expenseColor,
                            animationSpec = tween(600, easing = EaseInOutCubic),
                            label = "tabColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) tabColor.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) tabColor.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedType = type }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (type == TransactionType.INCOME)
                                        Icons.AutoMirrored.Filled.TrendingUp
                                    else
                                        Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isSelected) tabColor else textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) tabColor else textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // ── Quick Actions ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(SharedKeys.ROW_ITEMS),
                            animatedVisibilityScope = animatedVisibilityScope,
                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.AccountBalanceWallet,
                        label = "Add Source",
                        color = accentPurple,
                        onClick = { showAddSourceModal = true }
                    )
                    QuickActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Receipt,
                        label = "Add Receipt",
                        color = activeColor,
                        onClick = { showAddReceiptModal = true }
                    )
                }

                // ── Nav Cards ─────────────────────────────────────────────
                Text(
                    text = "BROWSE",
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )

                NavCard(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "Transaction Sources",
                    subtitle = "${txState.sources.size} ${selectedType.name.lowercase()} sources",
                    accentColor = accentPurple,
                    bgColor = bgCard,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onNavigateToSources(selectedType) }
                )

                NavCard(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Transaction Receipts",
                    subtitle = "${txState.receipts.size} receipts",
                    accentColor = activeColor,
                    bgColor = bgCard,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onNavigateToReceipts(selectedType) }
                )

                // ── Error ─────────────────────────────────────────────────
                txState.error?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(expenseColor.copy(alpha = 0.1f))
                            .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = expenseColor, modifier = Modifier.size(18.dp))
                            Text(
                                text = error,
                                color = expenseColor,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { transactionsViewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Close, "Dismiss", tint = expenseColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ── Save confirmation ─────────────────────────────────────
            ActionResultBar(
                result = txState.lastResult,
                onConsume = transactionsViewModel::consumeResult,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }

    if (showAddSourceModal) {
        SourceFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = selectedType,
            onDismiss = {
                transactionsViewModel.clearError()
                showAddSourceModal = false
            }
        )
    }

    if (showAddReceiptModal) {
        ReceiptFormModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            initialType = selectedType,
            onDismiss = {
                transactionsViewModel.clearError()
                showAddReceiptModal = false
            }
        )
    }

    // ── Export error snackbar ─────────────────────────────────────────────────
    exportSnackbarMsg?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            exportSnackbarMsg = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically { it },
                exit  = fadeOut() + slideOutVertically { it }
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(bgCard)
                        .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = expenseColor, modifier = Modifier.size(16.dp))
                    Text(msg, color = textPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grand Total Card  (with export dropdown)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GrandTotalCard(
    modifier: Modifier = Modifier,
    grandTotal: Double,
    currencyFormatter: NumberFormat,
    isExporting: Boolean = false,
    onExportCsv: () -> Unit = {},
    onExportPdf: () -> Unit = {}
) {
    val isPositive = grandTotal >= 0.0
    val totalColor by animateColorAsState(
        targetValue = if (isPositive) incomeColor else expenseColor,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "totalColor"
    )

    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        totalColor.copy(alpha = 0.12f),
                        totalColor.copy(alpha = 0.04f)
                    )
                )
            )
            .border(1.dp, totalColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        // ── Balance info ───────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top row: label + export button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NET BALANCE",
                    color = textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )

                // ── Export button + dropdown ──────────────────────────────
                Box {
                    // Export icon button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(totalColor.copy(alpha = 0.12f))
                            .border(1.dp, totalColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isExporting) { menuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isExporting,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "export_btn"
                        ) { exporting ->
                            if (exporting) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        color = totalColor,
                                        strokeWidth = 1.5.dp
                                    )
                                    Text(
                                        "Exporting…",
                                        color = totalColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.FileDownload,
                                        contentDescription = "Export",
                                        tint = totalColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        "Export",
                                        color = totalColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Dropdown menu
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(bgCard)
                            .border(1.dp, totalColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        // CSV option
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        "Export to CSV",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Spreadsheet · Excel compatible",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(incomeColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.TableChart,
                                        contentDescription = null,
                                        tint = incomeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onExportCsv()
                            }
                        )

                        HorizontalDivider(
                            color = textSecondary.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // PDF option
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        "Export to PDF",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Formatted report · Share ready",
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accentPurple.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = accentPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                onExportPdf()
                            }
                        )
                    }
                }
            }

            // Amount
            Text(
                text = buildString {
                    if (!isPositive) append("−")
                    append(currencyFormatter.format(abs(grandTotal)))
                },
                color = totalColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            // Subtitle
            Text(
                text = if (isPositive) "Income exceeds expenses" else "Expenses exceed income",
                color = textSecondary,
                fontSize = 12.sp
            )
        }
    }
}