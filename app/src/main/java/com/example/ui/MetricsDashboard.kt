package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceAlpha
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vision.AlertSeverity
import com.example.vision.BehavioralIndicators
import com.example.vision.BehavioralState
import com.example.vision.RawFeatureMetrics

@Composable
fun MetricsDashboard(
    indicators: BehavioralIndicators,
    rawMetrics: RawFeatureMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metrics_dashboard_card"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurfaceAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Drag indicator handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted.copy(alpha = 0.5f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Score Row
            TruthfulnessScoreRow(indicators)

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Sparkline Waveform
            if (indicators.timelineHistory.size > 2) {
                SparklineWaveform(
                    history = indicators.timelineHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Key Behavioral Telemetry Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Eye Dynamics Card
                TelemetrySubCard(
                    title = "EYE DYNAMICS",
                    primaryVal = "${indicators.blinksPerMinute.toInt()} BPM",
                    secondaryVal = "${String.format("%.1f", indicators.saccadeFrequency)} sacc/s",
                    statusColor = if (indicators.blinksPerMinute in 10f..32f) NeonEmerald else AlertAmber,
                    modifier = Modifier.weight(1f)
                )

                // Muscle Tension Card
                TelemetrySubCard(
                    title = "FACIAL TENSION",
                    primaryVal = if (rawMetrics.lipCompressionRatio < 0.10f) "LIP TENSE" else "RELAXED",
                    secondaryVal = if (rawMetrics.mouthAsymmetry > 0.045f) "ASYMMETRIC" else "BALANCED",
                    statusColor = if (rawMetrics.lipCompressionRatio < 0.10f || rawMetrics.mouthAsymmetry > 0.045f) DangerCrimson else NeonEmerald,
                    modifier = Modifier.weight(1f)
                )

                // Head Stability Card
                TelemetrySubCard(
                    title = "HEAD STABILITY",
                    primaryVal = "${rawMetrics.yaw.toInt()}° YAW",
                    secondaryVal = "${rawMetrics.pitch.toInt()}° PITCH",
                    statusColor = if (kotlin.math.abs(rawMetrics.yaw) > 18f) AlertAmber else NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cognitive Load & Hesitation Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricProgressBar(
                    label = "STRESS LOAD",
                    value = indicators.stressLevel,
                    barColor = if (indicators.stressLevel > 60) DangerCrimson else if (indicators.stressLevel > 35) AlertAmber else NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
                MetricProgressBar(
                    label = "HESITATION / STRAIN",
                    value = indicators.hesitationScore,
                    barColor = if (indicators.hesitationScore > 50) AlertAmber else NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            // Live Anomaly Alert Ticker
            AnimatedVisibility(visible = indicators.recentAnomalies.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "LIVE MICRO-EXPRESSION EVENTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    indicators.recentAnomalies.take(2).forEach { alert ->
                        AnomalyAlertItem(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun TruthfulnessScoreRow(indicators: BehavioralIndicators) {
    val animatedScore by animateFloatAsState(
        targetValue = indicators.truthfulnessScore.toFloat(),
        label = "score_anim"
    )

    val scoreColor by animateColorAsState(
        targetValue = when {
            indicators.truthfulnessScore >= 80 -> NeonEmerald
            indicators.truthfulnessScore >= 60 -> AlertAmber
            else -> DangerCrimson
        },
        label = "score_color"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Gauge
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(68.dp)) {
                // Background track
                drawArc(
                    color = CyberSurfaceVariant,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
                // Active score arc
                val sweep = (animatedScore / 100f) * 270f
                drawArc(
                    color = scoreColor,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${animatedScore.toInt()}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // State & Description
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scoreColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = indicators.dominantState.label.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = indicators.dominantState.description,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun TelemetrySubCard(
    title: String,
    primaryVal: String,
    secondaryVal: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = primaryVal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = statusColor
            )
            Text(
                text = secondaryVal,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun MetricProgressBar(
    label: String,
    value: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            Text(text = "$value%", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = barColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = CyberSurfaceVariant
        )
    }
}

@Composable
private fun SparklineWaveform(
    history: List<Float>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurface)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val w = size.width
            val h = size.height

            // Reference baseline (80%)
            val baselineY = h - (80f / 100f) * h
            drawLine(
                color = TextMuted.copy(alpha = 0.35f),
                start = Offset(0f, baselineY),
                end = Offset(w, baselineY),
                strokeWidth = 1f
            )

            if (history.size < 2) return@Canvas

            val stepX = w / (history.size - 1)
            val path = Path()

            history.forEachIndexed { i, score ->
                val x = i * stepX
                val y = h - (score / 100f) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(NeonCyan, NeonEmerald)),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun AnomalyAlertItem(alert: com.example.vision.AnomalyAlert) {
    val iconColor = when (alert.severity) {
        AlertSeverity.HIGH -> DangerCrimson
        AlertSeverity.MEDIUM -> AlertAmber
        AlertSeverity.LOW -> NeonCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(iconColor.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = alert.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = iconColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = alert.description,
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}
