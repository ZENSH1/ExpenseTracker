package com.xs.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
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
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    onLogout: () -> Unit,
    onNavigateToSources: (TransactionType) -> Unit,
    onNavigateToReceipts: (TransactionType) -> Unit,
    onProfileClicked: () -> Unit

) {
    val authState by authViewModel.uiState.collectAsState()
    val txState by transactionsViewModel.uiState.collectAsState()
    var showAddSourceModal by remember { mutableStateOf(false) }
    var showAddReceiptModal by remember { mutableStateOf(false) }

    val user = (authState as? AuthUiState.Authenticated)?.user
    val trackerId = user?.uid ?: return  // early return if not authenticated
    val onAddSource = { showAddSourceModal = true }
    val onAddReceipt = { showAddReceiptModal = true }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }

    // Observe sources & receipts whenever type or trackerId changes
    LaunchedEffect(trackerId, selectedType) {
        transactionsViewModel.observeSources(trackerId, selectedType)
        transactionsViewModel.observeReceipts(trackerId, "")
    }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale.getDefault())
    }

    // Compute grand total from sources
    val grandTotal = txState.sources.sumOf { it.totalAmount }

    val activeColor = if (selectedType == TransactionType.INCOME) incomeColor else expenseColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {

        // Ambient glow background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (selectedType == TransactionType.INCOME)
                            Color(0xFF00C9A7).copy(alpha = 0.12f)
                        else
                            Color(0xFFFF6B6B).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
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

            // ── Top Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.clickable(true){
                    onProfileClicked()
                }) {
                    Text(
                        text = "Hello,",
                        color = textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = user.displayName ?: "there",
                        color = textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // User avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentPurple.copy(alpha = 0.2f))
                            .border(1.5.dp, accentPurple.copy(alpha = 0.5f), CircleShape).clickable(true){
                                onProfileClicked()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user?.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = accentPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Logout
                    IconButton(
                        onClick = {
                            authViewModel.signOut()
                            onLogout()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = "Logout",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── Grand Total Card ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                bgCard,
                                Color(0xFF1E1E3A)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.4f),
                                accentPurple.copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(activeColor)
                        )
                        Text(
                            text = "Grand Total • ${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }

                    AnimatedContent(
                        targetState = grandTotal,
                        transitionSpec = {
                            slideInVertically { it } + fadeIn() togetherWith
                                    slideOutVertically { -it } + fadeOut()
                        },
                        label = "total_anim"
                    ) { total ->
                        Text(
                            text = currencyFormatter.format(total),
                            color = activeColor,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                    }

                    Text(
                        text = "${txState.sources.size} source${if (txState.sources.size != 1) "s" else ""}",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // ── Type Toggle ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TransactionType.values().forEach { type ->
                    val isSelected = selectedType == type
                    val tabColor = if (type == TransactionType.INCOME) incomeColor else expenseColor

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) tabColor.copy(alpha = 0.15f)
                                else Color.Transparent
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
                                    Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
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

            // ── Quick Action Buttons ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AccountBalanceWallet,
                    label = "Add Source",
                    color = accentPurple,
                    onClick = onAddSource
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Receipt,
                    label = "Add Receipt",
                    color = activeColor,
                    onClick = onAddReceipt
                )
            }

            // ── Navigation Cards ─────────────────────────────────────
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

            // ── Error snackbar area ──────────────────────────────────
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
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = expenseColor, modifier = Modifier.size(18.dp))
                        Text(text = error, color = expenseColor, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { transactionsViewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = expenseColor, modifier = Modifier.size(16.dp))
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


