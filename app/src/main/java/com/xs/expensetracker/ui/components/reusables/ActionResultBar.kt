package com.xs.expensetracker.ui.components.reusables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.utils.states.ActionResult
import kotlinx.coroutines.delay

/** How long a confirmation stays up before it clears itself. */
private const val VISIBLE_MILLIS = 2200L

/**
 * The "Receipt added" pill.
 *
 * Every screen that can write shows one, so a save is always acknowledged somewhere the user
 * is looking — previously a successful write left no trace at all beyond the list quietly
 * growing behind an open sheet.
 *
 * [onConsume] runs once the pill has had its time; the caller clears the result, which is also
 * what makes a second identical save show up as a second pill.
 */
@Composable
fun ActionResultBar(
    result: ActionResult?,
    onConsume: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Held separately from [result] because the result is cleared the moment the pill starts
    // animating out, and the text still has to be there while it goes.
    var message by remember { mutableStateOf("") }

    LaunchedEffect(result) {
        val current = result ?: return@LaunchedEffect
        message = current.message
        delay(VISIBLE_MILLIS)
        onConsume()
    }

    AnimatedVisibility(
        visible = result != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            initialOffsetY = { it }
        ),
        exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { it })
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(bgCard)
                .border(1.dp, incomeColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = incomeColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = message,
                color = textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
