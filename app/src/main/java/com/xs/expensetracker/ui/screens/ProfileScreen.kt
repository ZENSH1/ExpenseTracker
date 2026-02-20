package com.xs.expensetracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.xs.expensetracker.ui.components.reusables.ActionButton
import com.xs.expensetracker.ui.components.reusables.InfoRow
import com.xs.expensetracker.ui.components.reusables.SectionLabel
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import com.xs.expensetracker.utils.states.AuthUiState
import org.koin.androidx.compose.koinViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    val user = (state as? AuthUiState.Authenticated)?.user

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }

    // Logout confirmation
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = bgCard,
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
            text = { Text("You'll need to sign in again to access your data.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.signOut()
                    onLogout()
                }) { Text("Sign Out", color = expenseColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // Delete account confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteConfirmText = "" },
            containerColor = bgCard,
            titleContentColor = textPrimary,
            textContentColor = textSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = expenseColor, modifier = Modifier.size(20.dp))
                    Text("Delete Account?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("This will permanently delete your account and ALL associated data including trackers, sources, and receipts. This cannot be undone.")
                    Text(
                        "Type DELETE to confirm",
                        color = expenseColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        placeholder = { Text("DELETE", color = textSecondary.copy(alpha = 0.4f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = expenseColor.copy(alpha = 0.6f),
                            unfocusedBorderColor = textSecondary.copy(alpha = 0.2f),
                            cursorColor = expenseColor,
                            focusedContainerColor = bgDark,
                            unfocusedContainerColor = bgDark
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteConfirmText = ""
                        authViewModel.deleteAccount()
                        onLogout()
                    },
                    enabled = deleteConfirmText == "DELETE"
                ) {
                    Text(
                        "Delete Everything",
                        color = if (deleteConfirmText == "DELETE") expenseColor else textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteConfirmText = "" }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(bgDark)) {

        // Ambient glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentPurple.copy(alpha = 0.1f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.2f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.2f)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Profile", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                        }
                    },
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Avatar + Name ────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar
                    Box(contentAlignment = Alignment.Center) {
                        // Glow ring
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(accentPurple.copy(alpha = 0.3f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(accentPurple.copy(alpha = 0.4f), bgCard)
                                    )
                                )
                                .border(2.dp, Brush.linearGradient(colors = listOf(accentPurple, incomeColor.copy(alpha = 0.5f))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user?.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = textPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = user?.displayName ?: "Unknown User",
                            color = textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user?.email ?: "",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }

                    // Verified badge
                    if (user?.isEmailVerified == true) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(incomeColor.copy(alpha = 0.1f))
                                .border(1.dp, incomeColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(if (user.isEmailVerified) Icons.Filled.VerifiedUser else Icons.Filled.DeviceUnknown, contentDescription = null, tint = incomeColor, modifier = Modifier.size(13.dp))
                            Text(if (user.isEmailVerified) "Verified" else "Unverified", color = incomeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }else{
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(expenseColor.copy(alpha = 0.1f))
                                .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.DeviceUnknown, contentDescription = null, tint = incomeColor, modifier = Modifier.size(13.dp))
                            Text("Unverified", color = expenseColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ── Account Info Card ────────────────────────────────
                SectionLabel("ACCOUNT INFO")

                InfoCard {
                    InfoRow(icon = Icons.Filled.Person, label = "Display Name", value = user?.displayName ?: "—")
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), thickness = 0.5.dp)
                    InfoRow(icon = Icons.Filled.Email, label = "Email", value = user?.email ?: "—")
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), thickness = 0.5.dp)
                    InfoRow(icon = Icons.Filled.Fingerprint, label = "User ID", value = user?.uid ?: "—", valueColor = textSecondary)
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), thickness = 0.5.dp)
                    InfoRow(
                        icon = Icons.AutoMirrored.Filled.Login,
                        label = "Provider",
                        value = user?.providerData?.firstOrNull { it.providerId != "firebase" }?.providerId?.replaceFirstChar { it.uppercase() } ?: "Email"
                    )
                }

                // ── Actions ──────────────────────────────────────────
                SectionLabel("ACCOUNT ACTIONS")

                // Sign out
                ActionButton(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Sign Out",
                    sublabel = "You can sign back in anytime",
                    color = accentPurple,
                    onClick = { showLogoutDialog = true }
                )

                // Delete account
                ActionButton(
                    icon = Icons.Filled.DeleteForever,
                    label = "Delete Account & Data",
                    sublabel = "Permanently removes all your data",
                    color = expenseColor,
                    onClick = { showDeleteDialog = true }
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Account deletion is irreversible.\nAll trackers, sources and receipts will be lost.",
                    color = textSecondary.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ── Reusable Components ──────────────────────────────────────────────────────



@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgCard)
            .border(1.dp, textSecondary.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
        content = content
    )
}



