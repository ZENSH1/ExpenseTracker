package com.xs.expensetracker.ui.components.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.data.sync.SyncMode
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary

/**
 * Shown the first time an account syncs on a device that already holds data, when that account
 * also has data in the cloud.
 *
 * There is no dismiss action. Choosing is the only way forward, because every alternative
 * silently discards one side — and the user is the only one who knows which side matters. The
 * counts are shown so the decision is made against real numbers, not a guess.
 */
@Composable
fun LinkDecisionDialog(
    localRecords: Int,
    remoteTrackers: Int,
    onChoose: (SyncMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* deliberately not dismissible — see doc comment */ },
        containerColor = bgCard,
        titleContentColor = textPrimary,
        textContentColor = textSecondary,
        title = { Text("Two copies of your data", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "This device has $localRecords record(s), and your cloud account already " +
                        "has $remoteTrackers tracker(s). Choose which to keep.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                ChoiceRow(
                    icon = Icons.Filled.MergeType,
                    title = "Merge both",
                    subtitle = "Keeps everything from both sides. Nothing is lost.",
                    color = accentPurple,
                    onClick = { onChoose(SyncMode.MERGE) }
                )
                ChoiceRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Keep this device",
                    subtitle = "Cloud data is replaced by what's on this device.",
                    color = expenseColor,
                    onClick = { onChoose(SyncMode.FORCE_UPLOAD) }
                )
                ChoiceRow(
                    icon = Icons.Filled.CloudDownload,
                    title = "Keep the cloud",
                    subtitle = "This device's data is replaced by the cloud copy.",
                    color = incomeColor,
                    onClick = { onChoose(SyncMode.FORCE_DOWNLOAD) }
                )
            }
        },
        confirmButton = {}
    )
}

/**
 * Shown when a different account signs in on a device that still holds the previous account's
 * records.
 *
 * Proceeding automatically here would upload one person's data into another person's cloud, so
 * this is the one prompt the sync engine will not work around.
 */
@Composable
fun AccountChangeDialog(
    localRecords: Int,
    onChoose: (keepLocalData: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* deliberately not dismissible — see doc comment */ },
        containerColor = bgCard,
        titleContentColor = textPrimary,
        textContentColor = textSecondary,
        title = { Text("Different account signed in", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "This device holds $localRecords record(s) from a previous account. " +
                        "What should happen to them?",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                ChoiceRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "Move them to this account",
                    subtitle = "The records become yours and sync to the new account.",
                    color = accentPurple,
                    onClick = { onChoose(true) }
                )
                ChoiceRow(
                    icon = Icons.Filled.DeleteSweep,
                    title = "Remove them from this device",
                    subtitle = "Loads this account's cloud data instead. Anything not already " +
                        "synced to the previous account is lost.",
                    color = expenseColor,
                    onClick = { onChoose(false) }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
