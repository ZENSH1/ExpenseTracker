package com.xs.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.copy
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.Tracker
import com.xs.expensetracker.ui.components.modals.AddReceiptModal
import com.xs.expensetracker.ui.components.modals.AddSourceModal
import com.xs.expensetracker.ui.components.reusables.NavCard
import com.xs.expensetracker.ui.components.reusables.QuickActionButton
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.SharedKeys
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tracker: Tracker,                               // ← selected tracker passed from TrackerSelectionScreen
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    onLogout: () -> Unit,
    onNavigateToSources: (TransactionType) -> Unit,
    onNavigateToReceipts: (TransactionType) -> Unit,
    onProfileClicked: () -> Unit,
    onBack: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val txState   by transactionsViewModel.uiState.collectAsState()

    val user = (authState as? AuthUiState.Authenticated)?.user ?: return

    var showAddSourceModal  by remember { mutableStateOf(false) }
    var showAddReceiptModal by remember { mutableStateOf(false) }
    var selectedType        by remember { mutableStateOf(TransactionType.INCOME) }

    val trackerId = tracker.id

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

    LaunchedEffect(trackerId, selectedType) {
        transactionsViewModel.observeTracker(trackerId)
        transactionsViewModel.observeSources(trackerId, selectedType)
        transactionsViewModel.observeReceipts(trackerId, "")
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
                                text = txState.selectedTracker?.name?:"Unknown Tracker",
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
                                text = user.displayName ?: "there",
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
                                text = user.displayName?.firstOrNull()?.uppercase() ?: "?",
                                color = accentPurple,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = {
                            authViewModel.signOut()
                            onLogout()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Sign Out",
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
                            sharedContentState = rememberSharedContentState(SharedKeys.GRAND_TOTAL_CARD),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    grandTotal = txState.selectedTracker?.grandTotal?.toDouble()?:0.0,
                    currencyFormatter = currencyFormatter
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
                    icon = Icons.Outlined.ReceiptLong,
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
        }
    }

    if (showAddSourceModal) {
        AddSourceModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            onDismiss = {
                transactionsViewModel.clearError()
                showAddSourceModal = false
            }
        )
    }

    if (showAddReceiptModal) {
        AddReceiptModal(
            trackerId = trackerId,
            transactionsViewModel = transactionsViewModel,
            onDismiss = {
                transactionsViewModel.clearError()
                showAddReceiptModal = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grand Total Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GrandTotalCard(
    modifier: Modifier = Modifier,
    grandTotal: Double,
    currencyFormatter: NumberFormat
) {
    val isPositive  = grandTotal >= 0.0
    val totalColor  by animateColorAsState(
        targetValue = if (isPositive) incomeColor else expenseColor,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "totalColor"
    )

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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "NET BALANCE",
                color = textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Text(
                text = buildString {
                    if (!isPositive) append("−")
                    append(currencyFormatter.format(abs(grandTotal)))
                },
                color = totalColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isPositive) "Income exceeds expenses" else "Expenses exceed income",
                color = textSecondary,
                fontSize = 12.sp
            )
        }
    }
}