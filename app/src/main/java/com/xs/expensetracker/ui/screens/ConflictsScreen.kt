package com.xs.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.data.local.entity.ConflictEntityType
import com.xs.expensetracker.data.local.entity.SyncConflictEntity
import com.xs.expensetracker.data.sync.ConflictResolution
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.ui.viewmodels.SyncViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Per-record conflict resolution.
 *
 * A conflict means the same record was edited both here and elsewhere since the last sync.
 * Rather than guess, each one is listed with both versions side by side; nothing is applied
 * until the user picks. Bulk actions exist for the case where one side is simply known to be
 * right, but they are worded so it is clear what they discard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictsScreen(
    onBack: () -> Unit,
    syncViewModel: SyncViewModel = koinViewModel()
) {
    val conflicts by syncViewModel.conflicts.collectAsState()
    var bulkChoice by remember { mutableStateOf<ConflictResolution?>(null) }

    bulkChoice?.let { choice ->
        val keepingLocal = choice == ConflictResolution.KEEP_LOCAL
        AlertDialog(
            onDismissRequest = { bulkChoice = null },
            containerColor = bgCard,
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = {
                Text(
                    if (keepingLocal) "Keep all local versions?" else "Keep all cloud versions?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (keepingLocal) {
                        "All ${conflicts.size} conflicts will resolve to this device's version. " +
                            "The cloud versions will be overwritten on the next sync."
                    } else {
                        "All ${conflicts.size} conflicts will resolve to the cloud version. " +
                            "The changes made on this device will be discarded."
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    syncViewModel.resolveAllConflicts(choice)
                    bulkChoice = null
                }) {
                    Text("Confirm", color = expenseColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bulkChoice = null }) { Text("Cancel", color = textSecondary) }
            }
        )
    }

    Scaffold(
        containerColor = bgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review conflicts", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        if (conflicts.isNotEmpty()) {
                            Text(
                                "${conflicts.size} record(s) changed in two places",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (conflicts.isEmpty()) {
            EmptyConflicts(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BulkButton(
                        label = "Keep all local",
                        color = accentPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { bulkChoice = ConflictResolution.KEEP_LOCAL }
                    )
                    BulkButton(
                        label = "Keep all cloud",
                        color = incomeColor,
                        modifier = Modifier.weight(1f),
                        onClick = { bulkChoice = ConflictResolution.KEEP_REMOTE }
                    )
                }
            }

            items(conflicts, key = { it.id }) { conflict ->
                ConflictCard(
                    conflict = conflict,
                    onKeepLocal = {
                        syncViewModel.resolveConflict(conflict.id, ConflictResolution.KEEP_LOCAL)
                    },
                    onKeepRemote = {
                        syncViewModel.resolveConflict(conflict.id, ConflictResolution.KEEP_REMOTE)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictEntity,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgCard)
            .border(1.dp, expenseColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                conflict.entityType.label(),
                color = textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                conflict.label.ifBlank { "Untitled" },
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // A delete on one side against an edit on the other is the case most likely to surprise
        // someone, so it is called out rather than left to be inferred from two summaries.
        if (conflict.localDeleted != conflict.remoteDeleted) {
            Text(
                text = if (conflict.remoteDeleted) {
                    "Deleted on another device, but edited here."
                } else {
                    "Deleted here, but edited on another device."
                },
                color = expenseColor,
                fontSize = 12.sp
            )
        }

        VersionOption(
            icon = Icons.Filled.PhoneAndroid,
            heading = "This device",
            summary = conflict.localSummary,
            color = accentPurple,
            onClick = onKeepLocal
        )
        VersionOption(
            icon = Icons.Filled.CloudDownload,
            heading = "Cloud",
            summary = conflict.remoteSummary,
            color = incomeColor,
            onClick = onKeepRemote
        )
    }
}

@Composable
private fun VersionOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    heading: String,
    summary: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(heading, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = textPrimary, fontSize = 13.sp)
        }
        Text("Keep", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BulkButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyConflicts(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = incomeColor, modifier = Modifier.size(48.dp))
        Text(
            "No conflicts",
            color = textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            "Everything is in step across your devices.",
            color = textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun ConflictEntityType.label(): String = when (this) {
    ConflictEntityType.TRACKER -> "TRACKER"
    ConflictEntityType.SOURCE -> "SOURCE"
    ConflictEntityType.RECEIPT -> "RECEIPT"
}
