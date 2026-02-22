package com.xs.expensetracker.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xs.expensetracker.ui.theme.Blue40
import com.xs.expensetracker.ui.theme.ExpenseTrackerTheme
import com.xs.expensetracker.ui.theme.bgDark
import com.xs.expensetracker.ui.theme.textPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Phase machine ────────────────────────────────────────────────────────────
private enum class LoaderPhase {
    IDLE,
    SPHERE_LOADING,     // 1. spinning arc inside a sphere
    MORPHING_TO_TEXT,    // 2. sphere → pill box with text
    TEXT_VISIBLE,        // 2b. text showing inside pill
    MORPHING_TO_DROP,    // 3. pill → droplet shape
    DROPLET_READY,       // 3b. droplet fully formed
    DROPPING,            // 4. droplet falls
    SPREADING            // 5. splash ripple
}

@Composable
fun AnimationLoaderTestScreen() {

    var phase by remember { mutableStateOf(LoaderPhase.IDLE) }
    var isRunning by remember { mutableStateOf(false) }
    var loopKey by remember { mutableIntStateOf(0) }

    // ── Infinite spin for the arc loader ─────────────────────────────────
    val infiniteSpin = rememberInfiniteTransition(label = "spin")
    val arcSweepAngle by infiniteSpin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "arcAngle"
    )

    // ── Animatables ──────────────────────────────────────────────────────
    val morphToPill   = remember { Animatable(0f) }   // 0=circle  1=pill
    val morphToDrop   = remember { Animatable(0f) }   // 0=circle  1=droplet
    val arcAlpha      = remember { Animatable(1f) }
    val textAlpha     = remember { Animatable(0f) }
    val textScale     = remember { Animatable(0.6f) }
    val checkProgress = remember { Animatable(0f) }
    val dropProgress  = remember { Animatable(0f) }
    val spreadProgress = remember { Animatable(0f) }
    val spreadAlpha   = remember { Animatable(0f) }
    val shapeAlpha    = remember { Animatable(1f) }

    val density = LocalDensity.current
    val restingPaddingDp = 130f
    val restingPaddingPx = with(density) { restingPaddingDp.dp.toPx() }

    // ── Animation orchestrator ───────────────────────────────────────────
    LaunchedEffect(loopKey) {
        if (!isRunning) return@LaunchedEffect

        while (isRunning) {
            // Reset everything
            phase = LoaderPhase.SPHERE_LOADING
            morphToPill.snapTo(0f)
            morphToDrop.snapTo(0f)
            arcAlpha.snapTo(1f)
            textAlpha.snapTo(0f)
            textScale.snapTo(0.6f)
            checkProgress.snapTo(0f)
            dropProgress.snapTo(0f)
            spreadProgress.snapTo(0f)
            spreadAlpha.snapTo(0f)
            shapeAlpha.snapTo(1f)

            // ── 1. SPHERE LOADING — spin for 3s ─────────────────────────
            delay(3_000L)
            if (!isRunning) break

            // Checkmark before morphing
            checkProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            delay(300)

            // ── 2. MORPH SPHERE → PILL with text ────────────────────────
            phase = LoaderPhase.MORPHING_TO_TEXT
            launch { arcAlpha.animateTo(0f, tween(350)) }
            morphToPill.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            // Bring in text
            launch {
                textScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            textAlpha.animateTo(1f, tween(350))
            phase = LoaderPhase.TEXT_VISIBLE

            // ── 2b. Hold text for 3s ────────────────────────────────────
            delay(3_000L)
            if (!isRunning) break

            // ── 3. MORPH PILL → CIRCLE → DROPLET ────────────────────────
            phase = LoaderPhase.MORPHING_TO_DROP
            launch { textAlpha.animateTo(0f, tween(280)) }
            // pill → circle
            morphToPill.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(150)
            // circle → droplet
            morphToDrop.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            phase = LoaderPhase.DROPLET_READY
            delay(400)

            // ── 4. DROP with bounce ─────────────────────────────────────
            phase = LoaderPhase.DROPPING
            dropProgress.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
            )

            // ── 5. SPLASH ───────────────────────────────────────────────
            phase = LoaderPhase.SPREADING
            shapeAlpha.snapTo(0f)
            spreadAlpha.snapTo(1f)
            launch {
                spreadProgress.animateTo(
                    1f,
                    tween(950, easing = LinearOutSlowInEasing)
                )
            }
            spreadAlpha.animateTo(0f, tween(1_050))

            delay(350L)
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {

        // ── Splash ripples at the bottom ─────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.BottomCenter)
        ) {
            val alpha = spreadAlpha.value
            if (alpha > 0f) {
                val maxW = size.width * 0.88f
                val baseY = size.height * 0.28f

                repeat(3) { ring ->
                    val ringDelay = ring * 0.2f
                    val rp = ((spreadProgress.value - ringDelay) / (1f - ringDelay))
                        .coerceIn(0f, 1f)
                    if (rp > 0f) {
                        val ra = alpha * (1f - ring * 0.28f) * rp
                        val ew = maxW * rp
                        val eh = (14f + ring * 7f) * rp
                        val tl = Offset(center.x - ew / 2f, baseY - eh / 2f)
                        val sz = Size(ew, eh)
                        drawOval(Blue40.copy(alpha = ra * 0.14f), tl, sz)
                        drawOval(Blue40.copy(alpha = ra), tl, sz, style = Stroke(2.5f))
                    }
                }
            }
        }

        // ── Main morphing shape ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = restingPaddingDp.dp),
            contentAlignment = Alignment.Center
        ) {
            val sphereDp = 56.dp
            val pillWidthDp = 230.dp
            val pillHeightDp = 50.dp

            // Width/height interpolate: circle ↔ pill (droplet uses fixed sphere size)
            val canvasWidth =
                if (morphToDrop.value > 0f) sphereDp
                else sphereDp + (pillWidthDp - sphereDp) * morphToPill.value

            val canvasHeight =
                if (morphToDrop.value > 0f) sphereDp
                else sphereDp + (pillHeightDp - sphereDp) * morphToPill.value

            Canvas(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasHeight)
                    .graphicsLayer(
                        alpha = shapeAlpha.value,
                        translationY = restingPaddingPx * dropProgress.value
                    )
            ) {
                val w = size.width
                val h = size.height
                val dropFraction = morphToDrop.value

                if (dropFraction > 0f) {
                    // ── Morphing circle → droplet ────────────────────────
                    drawMorphingDroplet(Blue40, dropFraction)
                } else {
                    // ── Circle ↔ Pill ────────────────────────────────────
                    val cr = h / 2f
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Blue40.copy(alpha = 0.95f),
                                Blue40
                            ),
                            center = Offset(w * 0.38f, h * 0.32f),
                            radius = w.coerceAtLeast(h) * 0.85f
                        ),
                        cornerRadius = CornerRadius(cr, cr),
                        size = size
                    )
                    // Rim highlight
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.12f),
                        cornerRadius = CornerRadius(cr, cr),
                        size = size,
                        style = Stroke(1.8f)
                    )

                    // ── Spinning arc loader inside sphere ────────────────
                    val aAlpha = arcAlpha.value
                    if (aAlpha > 0f && morphToPill.value < 0.3f) {
                        val inset = w * 0.18f
                        val arcRect = Rect(inset, inset, w - inset, h - inset)

                        // Background track
                        drawArc(
                            color = Color.White.copy(alpha = 0.10f * aAlpha),
                            startAngle = 0f, sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcRect.topLeft, size = arcRect.size,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                        // Spinning arc (120° sweep)
                        drawArc(
                            color = Color.White.copy(alpha = 0.85f * aAlpha),
                            startAngle = arcSweepAngle, sweepAngle = 120f,
                            useCenter = false,
                            topLeft = arcRect.topLeft, size = arcRect.size,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )

                        // ── Checkmark ────────────────────────────────────
                        val cp = checkProgress.value
                        if (cp > 0f) {
                            val checkPath = Path().apply {
                                val cx = w / 2f
                                val cy = h / 2f
                                val s = w * 0.16f
                                val p1 = Offset(cx - s * 0.9f, cy + s * 0.05f)
                                val p2 = Offset(cx - s * 0.15f, cy + s * 0.75f)
                                val p3 = Offset(cx + s * 1.1f, cy - s * 0.7f)

                                moveTo(p1.x, p1.y)
                                if (cp < 0.45f) {
                                    val t = cp / 0.45f
                                    lineTo(
                                        p1.x + (p2.x - p1.x) * t,
                                        p1.y + (p2.y - p1.y) * t
                                    )
                                } else {
                                    lineTo(p2.x, p2.y)
                                    val t = (cp - 0.45f) / 0.55f
                                    lineTo(
                                        p2.x + (p3.x - p2.x) * t,
                                        p2.y + (p3.y - p2.y) * t
                                    )
                                }
                            }
                            drawPath(
                                checkPath,
                                color = Color.White.copy(alpha = aAlpha),
                                style = Stroke(
                                    width = 3f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            // ── "Loading Complete" text ───────────────────────────────────
            Text(
                text = "Loading Complete",
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer(
                    alpha = textAlpha.value,
                    scaleX = textScale.value,
                    scaleY = textScale.value
                )
            )
        }

        // ── Start / Stop Buttons ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isRunning) Blue40 else Blue40.copy(alpha = 0.3f))
                    .clickable(enabled = !isRunning) {
                        isRunning = true
                        loopKey++
                    }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Start",
                    color = if (!isRunning) Color.White else Color.White.copy(0.4f),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isRunning) Blue40 else Blue40.copy(0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = isRunning) {
                        isRunning = false
                        phase = LoaderPhase.IDLE
                    }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Stop",
                    color = if (isRunning) textPrimary else textPrimary.copy(0.3f),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── Morphing: circle (fraction=0) → water droplet (fraction=1) ──────────────
private fun DrawScope.drawMorphingDroplet(color: Color, fraction: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val radius = w.coerceAtMost(h) / 2f

    // Tip emerges upward as fraction grows
    val tipY = cy - radius - (radius * 1.15f * fraction)

    // Bezier control points widen smoothly into the neck
    val neckSpread = w * 0.42f * fraction
    val cpY = cy - radius + (tipY - (cy - radius)) * 0.45f

    val path = Path().apply {
        // Tip
        moveTo(cx, tipY)

        // Right curve: tip → right tangent of circle
        cubicTo(
            cx + neckSpread, cpY,
            cx + radius, cy - radius * 0.35f * fraction,
            cx + radius, cy
        )

        // Bottom semicircle — always a perfect arc
        arcTo(
            rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )

        // Left curve: left tangent → tip
        cubicTo(
            cx - radius, cy - radius * 0.35f * fraction,
            cx - neckSpread, cpY,
            cx, tipY
        )
        close()
    }

    // Soft glow layers
    val pivot = Offset(cx, cy + radius * 0.15f)
    for (layer in 3 downTo 1) {
        withTransform({ scale(1f + layer * 0.10f, 1f + layer * 0.10f, pivot) }) {
            drawPath(path, color = color.copy(alpha = 0.04f * layer / 3f))
        }
    }

    // Glossy radial-gradient fill
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                color.copy(alpha = 0.93f),
                color
            ),
            center = Offset(cx - w * 0.10f, cy - radius * 0.3f),
            radius = radius * 1.4f
        )
    )

    // Specular highlight bubble
    val hlR = radius * 0.28f
    val hlCenter = Offset(cx - radius * 0.22f, cy - radius * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.35f * (1f - fraction * 0.4f)),
                Color.Transparent
            ),
            center = hlCenter, radius = hlR
        ),
        center = hlCenter, radius = hlR
    )
}

// ─── Preview ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, backgroundColor = 0xFF0D0D14.toLong())
@Composable
fun AnimationLoaderTestScreenPreview() {
    ExpenseTrackerTheme {
        AnimationLoaderTestScreen()
    }
}