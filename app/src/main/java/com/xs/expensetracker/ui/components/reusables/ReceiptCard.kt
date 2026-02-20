package com.xs.expensetracker.ui.components.reusables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.data.enums.TransactionType
import com.xs.expensetracker.data.models.TransactionReceipt
import com.xs.expensetracker.ui.theme.accentPurple
import com.xs.expensetracker.ui.theme.bgCard
import com.xs.expensetracker.ui.theme.expenseColor
import com.xs.expensetracker.ui.theme.incomeColor
import com.xs.expensetracker.ui.theme.textPrimary
import com.xs.expensetracker.ui.theme.textSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date


@Composable
fun ReceiptCard(
    receipt: TransactionReceipt,
    sourceName: String,
    currency: NumberFormat,
    dateFormatter: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = if (receipt.type == TransactionType.INCOME) incomeColor else expenseColor
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgCard)
            .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date badge
        Column(
            modifier = Modifier.width(40.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val parts = dateFormatter.format(Date(receipt.date)).split(" ")
            Text(parts.getOrNull(0) ?: "", color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Text(parts.getOrNull(1) ?: "", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }

        // Info
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(receipt.name, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sourceName, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (receipt.description.isNotBlank()) {
                Text(receipt.description, color = textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Amount + menu
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${if (receipt.type == TransactionType.INCOME) "+" else "-"}${currency.format(receipt.amount)}",
                color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, modifier = Modifier.background(bgCard)) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = textPrimary, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = accentPurple, modifier = Modifier.size(16.dp)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = expenseColor, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = expenseColor, modifier = Modifier.size(16.dp)) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
