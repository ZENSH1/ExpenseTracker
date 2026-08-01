package com.xs.expensetracker.ui.components.reusables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.data.sync.SyncStatus
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textSecondary

/**
 * The sync symbol shown in app bars.
 *
 * Spins only while a sync is genuinely running. Every other state is a still icon, so motion in
 * this corner of the screen always means "something is happening right now" — which is what
 * makes it worth glancing at.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val visual = status.visual()

    val transition = rememberInfiniteTransition(label = "sync_spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "sync_angle"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (visual.emphasised) {
                    Modifier
                        .background(visual.tint.copy(alpha = 0.12f))
                        .border(1.dp, visual.tint.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                } else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (showLabel || visual.emphasised) 10.dp else 4.dp, vertical = 5.dp)
            // One description covering icon and label — a screen reader should announce the
            // sync state once, not read a decorative icon and then repeat it.
            .semantics { contentDescription = visual.description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedContent(
            targetState = visual.icon,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "sync_icon"
        ) { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier
                    .size(16.dp)
                    .then(if (status is SyncStatus.Syncing) Modifier.rotate(angle) else Modifier)
            )
        }

        if (showLabel || visual.emphasised) {
            Text(
                text = visual.label,
                color = visual.tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class SyncVisual(
    val icon: ImageVector,
    val tint: Color,
    val label: String,
    val description: String,
    /** Whether the state is worth a coloured pill rather than a bare icon. */
    val emphasised: Boolean = false
)

@Composable
private fun SyncStatus.visual(): SyncVisual = when (this) {
    SyncStatus.Offline -> SyncVisual(
        icon = Icons.Filled.CloudOff,
        tint = textSecondary,
        label = "Local only",
        description = "Not signed in. Your data is saved on this device only."
    )

    SyncStatus.UpToDate -> SyncVisual(
        icon = Icons.Filled.CloudDone,
        tint = incomeColor,
        label = "Synced",
        description = "All changes are synced."
    )

    SyncStatus.Syncing -> SyncVisual(
        icon = Icons.Filled.CloudSync,
        tint = accentPurple,
        label = "Syncing",
        description = "Syncing your changes."
    )

    is SyncStatus.Pending -> SyncVisual(
        icon = Icons.Filled.CloudQueue,
        tint = textSecondary,
        label = if (count == 1) "1 pending" else "$count pending",
        description = "$count change${if (count == 1) "" else "s"} waiting to sync."
    )

    is SyncStatus.Retrying -> SyncVisual(
        icon = Icons.Filled.SyncProblem,
        tint = textSecondary,
        label = "Retrying",
        description = "Sync failed and will retry automatically. $message"
    )

    is SyncStatus.Failed -> SyncVisual(
        icon = Icons.Filled.ErrorOutline,
        tint = expenseColor,
        label = "Sync failed",
        description = "Sync failed. $message",
        emphasised = true
    )

    is SyncStatus.ConflictsPending -> SyncVisual(
        icon = Icons.Filled.MergeType,
        tint = expenseColor,
        label = if (count == 1) "1 conflict" else "$count conflicts",
        description = "$count record${if (count == 1) "" else "s"} changed in two places. Tap to review.",
        emphasised = true
    )

    is SyncStatus.NeedsLinkDecision -> SyncVisual(
        icon = Icons.Filled.MergeType,
        tint = expenseColor,
        label = "Action needed",
        description = "Choose which copy of your data to keep. Tap to review.",
        emphasised = true
    )

    is SyncStatus.NeedsAccountDecision -> SyncVisual(
        icon = Icons.Filled.MergeType,
        tint = expenseColor,
        label = "Action needed",
        description = "A different account signed in. Tap to choose what happens to this device's data.",
        emphasised = true
    )
}
