package com.xs.expensetracker.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.ui.components.reusables.SectionLabel
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
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sync settings.
 *
 * The screen is written so that being signed out is an ordinary state, not a degraded one: the
 * account block invites sign-in, the sync actions are simply unavailable, and nothing suggests
 * the user is missing out on the app's actual function.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenConflicts: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val syncState by syncViewModel.state.collectAsState()
    val isBusy by syncViewModel.isBusy.collectAsState()
    val message by syncViewModel.actionMessage.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val appVersion = rememberAppVersion()

    var confirmImport by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            syncViewModel.consumeMessage()
        }
    }

    // ── Destructive-action confirmations ─────────────────────────────────────
    // Import and export both overwrite a whole side, so each states plainly what will be lost.

    if (confirmImport) {
        DestructiveDialog(
            title = "Replace local data?",
            body = "Everything on this device will be replaced with the copy in your cloud " +
                "account. Any changes here that haven't synced yet will be lost." +
                pendingWarning(syncState.pendingCount),
            confirmLabel = "Replace local",
            onConfirm = { confirmImport = false; syncViewModel.importFromCloud() },
            onDismiss = { confirmImport = false }
        )
    }

    if (confirmExport) {
        DestructiveDialog(
            title = "Replace cloud data?",
            body = "Your cloud account will be rewritten to match this device. Records that " +
                "exist only in the cloud — including ones added on your other devices — will " +
                "be removed.",
            confirmLabel = "Replace cloud",
            onConfirm = { confirmExport = false; syncViewModel.exportToCloud() },
            onDismiss = { confirmExport = false }
        )
    }

    if (confirmSignOut) {
        DestructiveDialog(
            title = "Sign out?",
            body = "Your data stays on this device and the app keeps working offline. " +
                "Syncing stops until you sign in again." +
                pendingWarning(syncState.pendingCount, signOut = true),
            confirmLabel = "Sign out",
            destructive = false,
            onConfirm = {
                confirmSignOut = false
                authViewModel.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    Scaffold(
        containerColor = bgDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Sync & Settings", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrimary)
                    }
                },
                actions = { SyncStatusIndicator(status = syncState.status, modifier = Modifier.padding(end = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ── Account ──────────────────────────────────────────────────────
            SectionLabel("CLOUD ACCOUNT")

            Card {
                if (syncState.isSignedIn) {
                    InfoLine("Signed in as", syncState.accountEmail ?: syncState.accountName ?: "—")
                    Divider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sign out",
                        subtitle = "Keeps your data on this device",
                        tint = textSecondary,
                        onClick = { confirmSignOut = true }
                    )
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "You're using the app without an account",
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Everything works offline and is saved on this device. Sign in only " +
                                "if you want to back up and sync across devices.",
                            color = textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Divider()
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Login,
                        title = "Sign in with Google",
                        subtitle = "Turn on cloud sync",
                        tint = accentPurple,
                        enabled = activity != null && authState !is AuthUiState.Loading,
                        onClick = { activity?.let { authViewModel.signIn(it) } }
                    )
                }
            }

            (authState as? AuthUiState.Error)?.let {
                ErrorNote(it.message)
            }

            // ── Sync status ──────────────────────────────────────────────────
            SectionLabel("SYNC STATUS")

            Card {
                InfoLine(
                    "Last synced",
                    syncState.lastSyncedAt?.let { dateFormat.format(Date(it)) } ?: "Never"
                )
                Divider()
                InfoLine(
                    "Waiting to upload",
                    if (syncState.pendingCount == 0) "Nothing pending" else "${syncState.pendingCount} change(s)"
                )

                if (syncState.conflictCount > 0) {
                    Divider()
                    SettingRow(
                        icon = Icons.Filled.MergeType,
                        title = "Review conflicts",
                        subtitle = "${syncState.conflictCount} record(s) changed in two places",
                        tint = expenseColor,
                        onClick = onOpenConflicts
                    )
                }
            }

            // ── Manual sync ──────────────────────────────────────────────────
            SectionLabel("MANUAL SYNC")

            Card {
                SettingRow(
                    icon = Icons.Filled.Sync,
                    title = "Sync now",
                    subtitle = "Two-way. Conflicts are flagged, never overwritten",
                    tint = accentPurple,
                    enabled = syncState.isSignedIn && !isBusy,
                    trailing = { if (isBusy) BusySpinner() },
                    onClick = { syncViewModel.syncNow() }
                )
                Divider()
                SettingRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Import from cloud",
                    subtitle = "Replaces everything on this device",
                    tint = incomeColor,
                    enabled = syncState.isSignedIn && !isBusy,
                    onClick = { confirmImport = true }
                )
                Divider()
                SettingRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "Export to cloud",
                    subtitle = "Replaces everything in your cloud account",
                    tint = expenseColor,
                    enabled = syncState.isSignedIn && !isBusy,
                    onClick = { confirmExport = true }
                )
            }

            if (!syncState.isSignedIn) {
                Text(
                    "Sign in to enable syncing. Your existing data will be kept — you'll be " +
                        "asked what to do if your account already has data in the cloud.",
                    color = textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }

            // ── Preferences ──────────────────────────────────────────────────
            SectionLabel("SYNC PREFERENCES")

            Card {
                ToggleRow(
                    icon = Icons.Filled.Sync,
                    title = "Auto-sync",
                    subtitle = "Sync in the background after changes",
                    checked = syncState.autoSyncEnabled,
                    enabled = syncState.isSignedIn,
                    onCheckedChange = syncViewModel::setAutoSync
                )
                Divider()
                ToggleRow(
                    icon = Icons.Filled.Wifi,
                    title = "Wi-Fi only",
                    subtitle = "Don't sync on mobile data",
                    checked = syncState.wifiOnly,
                    enabled = syncState.isSignedIn,
                    onCheckedChange = syncViewModel::setWifiOnly
                )
            }

            // ── About ────────────────────────────────────────────────────────
            SectionLabel("ABOUT")

            Card {
                InfoLine("Version", appVersion)
            }
        }
    }
}

/**
 * Version of the package actually installed, read from the system rather than from
 * BuildConfig, so a tester reporting a bug names the build they are really running even if
 * the APK was sideloaded over another.
 */
@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName ?: "?"} ($code)"
        }.getOrDefault("Unknown")
    }
}

/** Spells out the cost of a destructive action when there is genuinely something to lose. */
private fun pendingWarning(pendingCount: Int, signOut: Boolean = false): String = when {
    pendingCount <= 0 -> ""
    signOut -> "\n\n$pendingCount change(s) haven't reached the cloud yet — they'll sync when you sign back in."
    else -> "\n\n$pendingCount unsynced change(s) will be lost."
}

// ── Small building blocks ────────────────────────────────────────────────────

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgCard)
            .border(1.dp, textSecondary.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
    ) { content() }
}

@Composable
private fun Divider() =
    HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), thickness = 0.5.dp)

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textSecondary, fontSize = 13.sp)
        Text(
            value,
            color = textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = textPrimary.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSecondary.copy(alpha = alpha), fontSize = 11.sp, lineHeight = 15.sp)
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentPurple.copy(alpha = 0.12f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accentPurple.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = textPrimary.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSecondary.copy(alpha = alpha), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentPurple,
                uncheckedThumbColor = textSecondary,
                uncheckedTrackColor = bgDark
            )
        )
    }
}

@Composable
private fun BusySpinner() =
    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accentPurple)

@Composable
private fun ErrorNote(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(expenseColor.copy(alpha = 0.1f))
            .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(message, color = expenseColor, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun DestructiveDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgCard,
        titleContentColor = textPrimary,
        textContentColor = textSecondary,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) expenseColor else accentPurple,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textSecondary) }
        }
    )
}
