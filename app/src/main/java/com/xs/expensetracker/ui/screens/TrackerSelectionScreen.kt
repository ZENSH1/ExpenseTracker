package com.xs.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
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
import com.xs.expensetracker.domain.data.models.Tracker
import com.xs.expensetracker.ui.components.reusables.SyncStatusIndicator
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.ui.viewmodels.SyncViewModel
import com.xs.expensetracker.ui.viewmodels.TransactionsViewModel
import com.xs.expensetracker.utils.SharedKeys
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSelectionScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    authViewModel: AuthViewModel = koinViewModel(),
    transactionsViewModel: TransactionsViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel(),
    onTrackerSelected: (Tracker) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenConflicts: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsState()
    val txState   by transactionsViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()

    // No early return on auth: trackers come from the local database and exist whether or not
    // anyone is signed in.
    val user = (authState as? AuthUiState.Authenticated)?.user

    LaunchedEffect(Unit) {
        transactionsViewModel.observeTrackers()
    }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var trackerToEdit    by remember { mutableStateOf<Tracker?>(null) }
    var trackerToShare   by remember { mutableStateOf<Tracker?>(null) }
    var trackerToDelete  by remember { mutableStateOf<Tracker?>(null) }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
        ) {
            // Ambient glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentPurple.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.1f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, size.height * 0.1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 32.dp)
            ) {
                // ── Top Bar ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.clickable { onOpenProfile() }) {
                        Text(
                            text = if (user != null) "Welcome back," else "Your money",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = user?.displayName ?: "Expense Tracker",
                            color = textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedVisibility(
                            visible = txState.isLoading,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = accentPurple
                            )
                        }

                        // Tapping the sync symbol goes wherever it is pointing: to the conflict
                        // list when something needs resolving, to settings otherwise.
                        SyncStatusIndicator(
                            status = syncState.status,
                            onClick = {
                                if (syncState.conflictCount > 0) onOpenConflicts() else onOpenSettings()
                            }
                        )

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

                Spacer(modifier = Modifier.height(8.dp))

                // Loading message
                AnimatedVisibility(visible = txState.loadingMessage != null) {
                    txState.loadingMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = accentPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Section Header ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "YOUR TRACKERS",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${txState.trackers.size} total",
                        color = textSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Tracker List ─────────────────────────────────────────────
                // isInitialLoad separates "the database hasn't answered yet" from "there is
                // genuinely nothing here", so the empty state can't flash on every launch.
                if (txState.trackers.isEmpty() && !txState.isInitialLoad) {
                    EmptyTrackersState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onCreateClick = { showCreateDialog = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = txState.trackers,
                            key = { it.id }
                        ) { tracker ->
                            TrackerCard(
                                modifier = Modifier.fillMaxWidth()
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState("${SharedKeys.TRACKER_CARD}${tracker.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    ),
                                tracker = tracker,
                                isOwner = tracker.ownerId == txState.currentOwnerId,
                                currencyFormatter = currencyFormatter,
                                onClick = { onTrackerSelected(tracker) },
                                onEdit = { trackerToEdit = tracker },
                                onShare = { trackerToShare = tracker },
                                onDelete = { trackerToDelete = tracker }
                            )
                        }
                    }
                }
            }

            // ── FAB ─────────────────────────────────────────────────────────
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = accentPurple,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Tracker")
            }

            // ── Error Banner ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = txState.error != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 20.dp, end = 20.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                txState.error?.let { error ->
                    ErrorBanner(
                        message = error,
                        onDismiss = { transactionsViewModel.clearError() }
                    )
                }
            }
        }
    }
    // ── Dialogs ──────────────────────────────────────────────────────────

    if (showCreateDialog) {
        TrackerNameDialog(
            title = "New Tracker",
            confirmLabel = "Create",
            onConfirm = { name ->
                transactionsViewModel.createTracker(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    trackerToEdit?.let { tracker ->
        TrackerNameDialog(
            title = "Rename Tracker",
            initialValue = tracker.name,
            confirmLabel = "Save",
            onConfirm = { name ->
                transactionsViewModel.updateTrackerName(tracker.id, name)
                trackerToEdit = null
            },
            onDismiss = { trackerToEdit = null }
        )
    }

    trackerToShare?.let { tracker ->
        ShareTrackerDialog(
            onConfirm = { userId ->
                transactionsViewModel.shareTracker(tracker.id, userId)
                trackerToShare = null
            },
            onDismiss = { trackerToShare = null }
        )
    }

    trackerToDelete?.let { tracker ->
        DeleteTrackerDialog(
            trackerName = tracker.name,
            onConfirm = {
                transactionsViewModel.deleteTracker(tracker.id)
                trackerToDelete = null
            },
            onDismiss = { trackerToDelete = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tracker Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerCard(
    modifier: Modifier = Modifier,
    tracker: Tracker,
    isOwner: Boolean,
    currencyFormatter: NumberFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val isPositive = tracker.grandTotal >= 0.0
    val grandTotalColor by animateColorAsState(
        targetValue = if (isPositive) incomeColor else expenseColor,
        animationSpec = tween(400),
        label = "grandTotalColor"
    )

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            grandTotalColor.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = grandTotalColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ── Row 1: Name + Menu ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(grandTotalColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = grandTotalColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = tracker.name,
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isOwner) {
                                Text(
                                    text = "Shared with you",
                                    color = accentPurple.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Options",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(bgCard)
                        ) {
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = textPrimary, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = { menuExpanded = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share", color = textPrimary, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Share, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = { menuExpanded = false; onShare() }
                                )
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
                                DropdownMenuItem(
                                    text = { Text("Delete", color = expenseColor, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Delete, null, tint = expenseColor, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = { menuExpanded = false; onDelete() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Leave Tracker", color = expenseColor, fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = expenseColor, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = { menuExpanded = false; onDelete() }
                                )
                            }
                        }
                    }
                }

                // ── Divider ─────────────────────────────────────────────
                HorizontalDivider(color = textSecondary.copy(alpha = 0.08f))

                // ── Row 2: Grand Total ───────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NET BALANCE",
                            color = textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                if (!isPositive) append("−")
                                append(currencyFormatter.format(abs(tracker.grandTotal)))
                            },
                            color = grandTotalColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositive)
                                Icons.AutoMirrored.Filled.TrendingUp
                            else
                                Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = grandTotalColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isPositive) "Positive" else "Negative",
                            color = grandTotalColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyTrackersState(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accentPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = accentPurple.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No trackers yet",
            color = textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Create your first tracker to start\nmanaging your expenses.",
            color = textSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onCreateClick,
            border = BorderStroke(1.dp, accentPurple.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentPurple)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create Tracker", fontSize = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrackerNameDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialValue) }
    val isError = name.isBlank() && name != initialValue

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgCard,
        title = {
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g. Monthly Budget", color = textSecondary) },
                isError = isError,
                supportingText = if (isError) {
                    { Text("Name cannot be empty", color = expenseColor, fontSize = 12.sp) }
                } else null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentPurple,
                    unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    cursorColor = accentPurple
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = accentPurple),
                enabled = name.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

@Composable
private fun ShareTrackerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgCard,
        title = {
            Text("Share Tracker", color = textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the user ID of the person you want to share this tracker with.",
                    color = textSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    placeholder = { Text("User ID", color = textSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentPurple,
                        unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = accentPurple
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (userId.isNotBlank()) onConfirm(userId) },
                colors = ButtonDefaults.buttonColors(containerColor = accentPurple),
                enabled = userId.isNotBlank()
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

@Composable
private fun DeleteTrackerDialog(
    trackerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgCard,
        icon = {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = expenseColor
            )
        },
        title = {
            Text("Delete Tracker?", color = textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "\"$trackerName\" and all its data will be permanently deleted. This cannot be undone.",
                color = textSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = expenseColor)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(expenseColor.copy(alpha = 0.1f))
            .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.ErrorOutline, null, tint = expenseColor, modifier = Modifier.size(18.dp))
            Text(
                text = message,
                color = expenseColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", tint = expenseColor, modifier = Modifier.size(14.dp))
            }
        }
    }
}