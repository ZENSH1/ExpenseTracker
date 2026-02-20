package com.xs.expensetracker.ui.screens

import com.xs.expensetracker.utils.AuthUiState
import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.xs.expensetracker.R
import com.xs.expensetracker.ui.theme.*
import com.xs.expensetracker.ui.viewmodels.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = koinViewModel(),
    onLoginSuccess: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    if (state is AuthUiState.Authenticated) {
        LaunchedEffect(Unit) { onLoginSuccess() }
    }

    // Pulse animation for the logo glow
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        // Ambient background glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentPurple.copy(alpha = glowAlpha * 0.6f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.25f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        incomeColor.copy(alpha = glowAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.75f),
                    radius = size.width * 0.5f
                ),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.15f, size.height * 0.75f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        expenseColor.copy(alpha = glowAlpha * 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.7f),
                    radius = size.width * 0.4f
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.85f, size.height * 0.7f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo ────────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentPurple.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Icon container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accentPurple.copy(alpha = 0.3f),
                                    bgCard
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    accentPurple.copy(alpha = 0.7f),
                                    incomeColor.copy(alpha = 0.3f)
                                )
                            ),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.money_euro_svgrepo_com),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = Color.Unspecified
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Title ────────────────────────────────────────────────
            Text(
                text = "Expense Tracker",
                color = textPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Track income & expenses.\nStay in control of your money.",
                color = textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(56.dp))

            // ── Google Sign In Button ────────────────────────────────
            val isLoading = state is AuthUiState.Loading

            Button(
                onClick = { if (!isLoading) authViewModel.signIn(activity) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledContainerColor = Color.White.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "btn_content"
                ) { loading ->
                    if (loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF1F1F1F),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Signing in...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Google "G" logo drawn with canvas
                            GoogleGLogo(modifier = Modifier.size(20.dp))
                            Text(
                                "Continue with Google",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Status message ───────────────────────────────────────
            AnimatedVisibility(
                visible = state is AuthUiState.Error,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                if (state is AuthUiState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(expenseColor.copy(alpha = 0.1f))
                            .border(1.dp, expenseColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = (state as AuthUiState.Error).message,
                            color = expenseColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // ── Footer ───────────────────────────────────────────────
            Text(
                text = "By continuing, you agree to our\nTerms of Service & Privacy Policy",
                color = textSecondary.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.28f)
        val oval = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        val topLeft = Offset(cx - r, cy - r)

        // Red: bottom-right, stops at ~-30 (330°)
        drawArc(color = Color(0xFFEA4335), startAngle = 235f, sweepAngle = 95f,
            useCenter = false, topLeft = topLeft, size = oval, style = stroke)
        // Yellow: bottom-left
        drawArc(color = Color(0xFFFBBC05), startAngle = 175f, sweepAngle = 60f,
            useCenter = false, topLeft = topLeft, size = oval, style = stroke)
        // Green: left to bottom
        drawArc(color = Color(0xFF34A853), startAngle = 120f, sweepAngle = 55f,
            useCenter = false, topLeft = topLeft, size = oval, style = stroke)
        // Blue: top sweeping right, leaves gap on right side for the crossbar
        drawArc(color = Color(0xFF4285F4), startAngle = -60f, sweepAngle = 180f,
            useCenter = false, topLeft = topLeft, size = oval, style = stroke)

        // Horizontal crossbar of G (right side only, at vertical center)
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(cx, cy - r * 0.14f),
            size = androidx.compose.ui.geometry.Size(r * 0.95f, r * 0.28f)
        )
    }
}