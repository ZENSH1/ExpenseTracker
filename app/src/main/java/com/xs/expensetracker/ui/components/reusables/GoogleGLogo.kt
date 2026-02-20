package com.xs.expensetracker.ui.components.reusables

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

@Composable
fun GoogleGLogo(modifier: Modifier = Modifier) {

    Canvas(
        modifier = modifier
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = minOf(canvasWidth, canvasHeight) / 2f
        val cx = canvasWidth / 2f
        val cy = canvasHeight / 2f

        // 2. Size the stroke precisely to match standard Google branding proportions
        val strokeWidth = radius * 0.42f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        // The arc drawing bounds are based on the center of the stroke,
        // so we subtract half the stroke width to keep it perfectly inside the canvas bounds.
        val arcRadius = radius - strokeWidth / 2f
        val ovalSize = Size(arcRadius * 2, arcRadius * 2)
        val topLeft = Offset(cx - arcRadius, cy - arcRadius)

        // Draw Yellow (Left side)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = ovalSize,
            style = stroke
        )

        // Draw Green (Bottom side)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = ovalSize,
            style = stroke
        )

        // Draw Red (Top side)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = topLeft,
            size = ovalSize,
            style = stroke
        )

        // Draw Blue (Right side + Crossbar)
        // We clip the top so the crossbar has a perfectly flat horizontal edge
        // while the outside remains a smooth circle.
        clipRect(
            top = cy - strokeWidth / 2f,
            bottom = canvasHeight
        ) {
            drawArc(
                color = Color(0xFF4285F4),
                // Start slightly higher (negative angle) so it gets cleanly chopped by clipRect
                startAngle = -20f,
                sweepAngle = 65f,
                useCenter = false,
                topLeft = topLeft,
                size = ovalSize,
                style = stroke
            )
        }

        // Fill in the horizontal crossbar to connect the center to the blue arc
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(cx, cy - strokeWidth / 2f),
            size = Size(arcRadius, strokeWidth)
        )
    }
}