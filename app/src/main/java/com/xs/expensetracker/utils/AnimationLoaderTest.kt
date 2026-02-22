package com.xs.expensetracker.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.ui.theme.Blue40
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.textPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Phase machine ────────────────────────────────────────────────────────────
private enum class LoaderPhase { SPINNING, TEXT_VISIBLE, DROPPING, SPREADING }

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun AnimationLoaderTestScreen() {

    var phase by remember { mutableStateOf(LoaderPhase.SPINNING) }

    // Continuous 360° spin – always running, only consumed during SPINNING phase
    val infiniteSpin = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteSpin.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )

    // Pulsing glow intensity while spinning
    val glowPulse by infiniteSpin.animateFloat(
        initialValue  = 0.04f,
        targetValue   = 0.12f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glow"
    )

    val dropletAlpha   = remember { Animatable(1f) }
    val textAlpha      = remember { Animatable(0f) }
    val textScale      = remember { Animatable(0.7f) }
    val dropProgress   = remember { Animatable(0f) }   // 0 = resting → 1 = at bottom
    val spreadProgress = remember { Animatable(0f) }   // 0 = no spread → 1 = fully spread
    val spreadAlpha    = remember { Animatable(0f) }

    val density          = LocalDensity.current
    val restingPaddingDp = 110f                                                    // dp above bottom
    val restingPaddingPx = with(density) { restingPaddingDp.dp.toPx() }

    // ── Animation loop ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {

            // Reset to initial state
            phase = LoaderPhase.SPINNING
            dropletAlpha.snapTo(1f)
            textAlpha.snapTo(0f)
            textScale.snapTo(0.7f)
            dropProgress.snapTo(0f)
            spreadProgress.snapTo(0f)
            spreadAlpha.snapTo(0f)

            // ── 1. SPIN 3 s ───────────────────────────────────────────────
            delay(3_000L)

            // ── 2. MORPH droplet → text ───────────────────────────────────
            launch { dropletAlpha.animateTo(0f, tween(350)) }
            launch {
                textScale.animateTo(
                    targetValue   = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    )
                )
            }
            textAlpha.animateTo(1f, tween(380))
            phase = LoaderPhase.TEXT_VISIBLE

            // ── 3. TEXT visible 3 s ───────────────────────────────────────
            delay(3_000L)

            // ── 4. MORPH text → droplet ───────────────────────────────────
            launch { textAlpha.animateTo(0f, tween(300)) }
            delay(160)
            dropletAlpha.animateTo(1f, tween(300))
            delay(220)

            // ── 5. DROP ───────────────────────────────────────────────────
            phase = LoaderPhase.DROPPING
            dropProgress.animateTo(1f, tween(520, easing = FastOutLinearInEasing))

            // ── 6. SPREAD + FADE ──────────────────────────────────────────
            phase = LoaderPhase.SPREADING
            dropletAlpha.snapTo(0f)
            spreadAlpha.snapTo(1f)
            launch { spreadProgress.animateTo(1f, tween(950, easing = LinearOutSlowInEasing)) }
            spreadAlpha.animateTo(0f, tween(1_050))

            delay(250L)
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {

        // ── Water ripple / splash at the very bottom ─────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.BottomCenter)
        ) {
            val alpha = spreadAlpha.value
            if (alpha > 0f) {
                val maxW  = size.width * 0.88f
                val baseY = size.height * 0.28f   // anchor: near the top of this canvas

                repeat(3) { ring ->
                    val ringDelay = ring * 0.2f
                    val rp        = ((spreadProgress.value - ringDelay) / (1f - ringDelay))
                        .coerceIn(0f, 1f)

                    if (rp > 0f) {
                        val ra  = alpha * (1f - ring * 0.28f) * rp
                        val ew  = maxW * rp
                        val eh  = (14f + ring * 7f) * rp
                        val tl  = Offset(center.x - ew / 2f, baseY - eh / 2f)
                        val sz  = Size(ew, eh)

                        // Soft filled core
                        drawOval(Blue40.copy(alpha = ra * 0.14f), tl, sz)
                        // Crisp ring stroke
                        drawOval(Blue40.copy(alpha = ra), tl, sz, style = Stroke(2.5f))
                    }
                }
            }
        }

        // ── Droplet + Text – anchored 110dp above the bottom ─────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = restingPaddingDp.dp),
            contentAlignment = Alignment.Center
        ) {

            // Droplet canvas
            Canvas(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer(
                        alpha       = dropletAlpha.value,
                        translationY = restingPaddingPx * dropProgress.value
                    )
            ) {
                val angle       = if (phase == LoaderPhase.SPINNING) spinAngle else 0f
                val glowStrength = if (phase == LoaderPhase.SPINNING) glowPulse else 0.04f
                rotate(angle) {
                    drawWaterDroplet(Blue40, glowStrength)
                }
            }

            // "Loading Complete" text
            Text(
                text     = "Loading Complete",
                color    = textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer(
                    alpha  = textAlpha.value,
                    scaleX = textScale.value,
                    scaleY = textScale.value
                )
            )
        }
    }
}

// ─── Water droplet shape ──────────────────────────────────────────────────────
private fun DrawScope.drawWaterDroplet(color: Color, glowStrength: Float = 0.07f) {
    val w  = size.width
    val h  = size.height
    val cx = w / 2f

    // Teardrop path: tip at top, circular base at bottom
    val path = Path().apply {
        moveTo(cx, 0f)

        // Right bezier down to the circle's 3 o'clock position
        cubicTo(
            cx + w * 0.45f, h * 0.18f,
            w,              h * 0.56f,
            w,              h * 0.71f
        )

        // Bottom semicircle (clockwise from 0° to 180°)
        arcTo(
            rect           = Rect(0f, h * 0.42f, w, h),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo    = false
        )

        // Left bezier back up to tip
        cubicTo(
            0f,              h * 0.56f,
            cx - w * 0.45f, h * 0.18f,
            cx,              0f
        )
        close()
    }

    // Soft glow layers (scale upward from the circular center)
    val pivot = Offset(cx, h * 0.65f)
    for (layer in 3 downTo 1) {
        withTransform({
            scale(1f + layer * 0.13f, 1f + layer * 0.13f, pivot)
        }) {
            drawPath(path, color = color.copy(alpha = glowStrength * layer / 3f))
        }
    }

    // Glossy radial-gradient fill (light source top-left)
    drawPath(
        path  = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.52f),
                color.copy(alpha = 0.92f),
                color
            ),
            center = Offset(cx - w * 0.12f, h * 0.24f),
            radius = w * 0.70f
        )
    )
}

// ─── Preview ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, backgroundColor = 0xFF0D0D14.toLong())
@Composable
fun AnimationLoaderTestScreenPreview() {
    AnimationLoaderTestScreen()
}
