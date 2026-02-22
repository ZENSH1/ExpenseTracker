package com.xs.expensetracker.ui.components.reusables

import android.content.ClipData
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import com.xs.expensetracker.utils.Utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = textPrimary
) {
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current

    val copyToClipboard: () -> Unit = {
        CoroutineScope(Dispatchers.Default).launch {
            clipboardManager.setClipEntry(
                clipEntry = ClipEntry(
                    ClipData.newPlainText(
                        "text",
                        value
                    )
                )
            )
            showToast(context,"Copied to clipboard")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clickable(true) {
                copyToClipboard()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentPurple.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = textSecondary, fontSize = 11.sp)
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}