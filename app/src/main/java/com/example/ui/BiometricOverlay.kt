package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.vision.FaceMeshResult
import com.example.vision.Point3D
import com.example.vision.RawFeatureMetrics

/**
 * Real-time biometric canvas overlay rendering face mesh wireframe,
 * eye contours, gaze rays, and micro-tension highlight regions.
 */
@Composable
fun BiometricOverlay(
    mesh: FaceMeshResult,
    metrics: RawFeatureMetrics,
    truthfulnessScore: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reticle_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val targetColor by animateColorAsState(
        targetValue = when {
            truthfulnessScore >= 80 -> NeonCyan
            truthfulnessScore >= 60 -> AlertAmber
            else -> DangerCrimson
        },
        label = "target_color"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!mesh.hasFace || mesh.points.size < 468) {
            // Draw scanning idle reticle
            drawIdleScanningReticle(pulseAlpha)
            return@Canvas
        }

        val canvasW = size.width
        val canvasH = size.height
        val pts = mesh.points

        // Helper to convert normalized coordinate (0..1) to canvas pixels
        fun pt(p: Point3D): Offset = Offset(p.x * canvasW, p.y * canvasH)

        // 1. Draw Face Wireframe Contours
        drawEyeContour(pts, leftEye = true, canvasW, canvasH, targetColor)
        drawEyeContour(pts, leftEye = false, canvasW, canvasH, targetColor)
        drawEyebrows(pts, canvasW, canvasH, targetColor)
        drawLips(pts, canvasW, canvasH, targetColor, metrics.lipCompressionRatio)
        drawNoseAndJaw(pts, canvasW, canvasH, targetColor.copy(alpha = 0.35f))

        // 2. Gaze Tracking Vector Rays
        if (pts.size >= 478) {
            drawGazeRays(pts, canvasW, canvasH, targetColor)
        }

        // 3. Highlight Tension Zones
        if (metrics.mouthAsymmetry > 0.045f || metrics.lipCompressionRatio < 0.10f) {
            // Mouth micro-tension alert aura
            val mouthCenter = pt(pts[13])
            drawCircle(
                color = DangerCrimson.copy(alpha = 0.25f * pulseAlpha),
                radius = 32f,
                center = mouthCenter
            )
        }

        // 4. Tactical Bounding Reticle around the face
        mesh.boundingBox?.let { box ->
            val left = box.left * canvasW - 20f
            val top = box.top * canvasH - 30f
            val right = box.right * canvasW + 20f
            val bottom = box.bottom * canvasH + 20f
            val w = right - left
            val h = bottom - top

            drawTacticalReticle(left, top, w, h, targetColor, pulseAlpha)
        }
    }
}

private fun DrawScope.drawIdleScanningReticle(alpha: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = minOf(size.width, size.height) * 0.32f

    // Concentric guide circles
    drawCircle(
        color = NeonCyan.copy(alpha = 0.15f * alpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f)
    )
    drawCircle(
        color = NeonCyan.copy(alpha = 0.3f * alpha),
        radius = radius * 0.7f,
        center = Offset(cx, cy),
        style = Stroke(width = 1f)
    )

    // Center crosshairs
    val arm = 24f
    drawLine(
        color = NeonCyan.copy(alpha = 0.5f * alpha),
        start = Offset(cx - arm, cy),
        end = Offset(cx + arm, cy),
        strokeWidth = 2f
    )
    drawLine(
        color = NeonCyan.copy(alpha = 0.5f * alpha),
        start = Offset(cx, cy - arm),
        end = Offset(cx, cy + arm),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawTacticalReticle(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    color: Color,
    alpha: Float
) {
    val cornerLen = minOf(w, h) * 0.15f
    val stroke = Stroke(width = 2.5f, cap = StrokeCap.Square)

    // Top-Left
    drawLine(color.copy(alpha = alpha), Offset(x, y), Offset(x + cornerLen, y), stroke.width)
    drawLine(color.copy(alpha = alpha), Offset(x, y), Offset(x, y + cornerLen), stroke.width)

    // Top-Right
    val r = x + w
    drawLine(color.copy(alpha = alpha), Offset(r, y), Offset(r - cornerLen, y), stroke.width)
    drawLine(color.copy(alpha = alpha), Offset(r, y), Offset(r, y + cornerLen), stroke.width)

    // Bottom-Left
    val b = y + h
    drawLine(color.copy(alpha = alpha), Offset(x, b), Offset(x + cornerLen, b), stroke.width)
    drawLine(color.copy(alpha = alpha), Offset(x, b), Offset(x, b - cornerLen), stroke.width)

    // Bottom-Right
    drawLine(color.copy(alpha = alpha), Offset(r, b), Offset(r - cornerLen, b), stroke.width)
    drawLine(color.copy(alpha = alpha), Offset(r, b), Offset(r, b - cornerLen), stroke.width)
}

private fun DrawScope.drawEyeContour(
    pts: List<Point3D>,
    leftEye: Boolean,
    w: Float,
    h: Float,
    color: Color
) {
    val indices = if (leftEye) {
        listOf(33, 160, 158, 133, 153, 144, 33)
    } else {
        listOf(362, 385, 387, 263, 373, 380, 362)
    }

    val path = Path().apply {
        val first = pts[indices[0]]
        moveTo(first.x * w, first.y * h)
        for (i in 1 until indices.size) {
            val p = pts[indices[i]]
            lineTo(p.x * w, p.y * h)
        }
    }
    drawPath(path, color.copy(alpha = 0.7f), style = Stroke(width = 1.8f))
}

private fun DrawScope.drawEyebrows(
    pts: List<Point3D>,
    w: Float,
    h: Float,
    color: Color
) {
    val leftBrow = listOf(70, 63, 105, 66, 107)
    val rightBrow = listOf(336, 296, 334, 293, 300)

    fun drawLineStrip(indices: List<Int>) {
        val path = Path().apply {
            val first = pts[indices[0]]
            moveTo(first.x * w, first.y * h)
            for (i in 1 until indices.size) {
                val p = pts[indices[i]]
                lineTo(p.x * w, p.y * h)
            }
        }
        drawPath(path, color.copy(alpha = 0.8f), style = Stroke(width = 2f, cap = StrokeCap.Round))
    }

    drawLineStrip(leftBrow)
    drawLineStrip(rightBrow)
}

private fun DrawScope.drawLips(
    pts: List<Point3D>,
    w: Float,
    h: Float,
    color: Color,
    compressionRatio: Float
) {
    val outerLip = listOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146, 61)
    val path = Path().apply {
        val first = pts[outerLip[0]]
        moveTo(first.x * w, first.y * h)
        for (i in 1 until outerLip.size) {
            val p = pts[outerLip[i]]
            lineTo(p.x * w, p.y * h)
        }
    }
    val lipColor = if (compressionRatio < 0.10f) DangerCrimson else color
    drawPath(path, lipColor.copy(alpha = 0.75f), style = Stroke(width = 1.6f))
}

private fun DrawScope.drawNoseAndJaw(
    pts: List<Point3D>,
    w: Float,
    h: Float,
    color: Color
) {
    // Nose bridge
    val nose = listOf(168, 6, 197, 195, 5, 4, 1)
    val nosePath = Path().apply {
        moveTo(pts[nose[0]].x * w, pts[nose[0]].y * h)
        for (i in 1 until nose.size) {
            lineTo(pts[nose[i]].x * w, pts[nose[i]].y * h)
        }
    }
    drawPath(nosePath, color, style = Stroke(width = 1.2f))
}

private fun DrawScope.drawGazeRays(
    pts: List<Point3D>,
    w: Float,
    h: Float,
    color: Color
) {
    val lPupil = pts[468]
    val rPupil = pts[473]

    val leftOrigin = Offset(lPupil.x * w, lPupil.y * h)
    val rightOrigin = Offset(rPupil.x * w, rPupil.y * h)

    // Draw glowing iris node
    drawCircle(NeonCyan, radius = 3.5f, center = leftOrigin)
    drawCircle(NeonCyan, radius = 3.5f, center = rightOrigin)

    // Ray vector
    val rayLength = 36f
    drawLine(
        color = NeonEmerald.copy(alpha = 0.8f),
        start = leftOrigin,
        end = Offset(leftOrigin.x, leftOrigin.y + rayLength),
        strokeWidth = 2f
    )
    drawLine(
        color = NeonEmerald.copy(alpha = 0.8f),
        start = rightOrigin,
        end = Offset(rightOrigin.x, rightOrigin.y + rayLength),
        strokeWidth = 2f
    )
}
