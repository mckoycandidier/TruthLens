package com.example.vision

import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Aggregates temporal facial micro-expression signals over a sliding time window (3-5s)
 * to estimate behavioral stress, cognitive load, hesitation, and composure index.
 */
class TruthfulnessScorer {

    private val windowMs = 4000L // 4-second sliding window
    private val history = ArrayDeque<RawFeatureMetrics>()
    private val scoreHistory = ArrayDeque<Float>() // Sparkline timeline history (last 60 frames)

    // Calibration baseline
    var calibrationData = CalibrationData()
        private set
    private val calibrationSamples = mutableListOf<RawFeatureMetrics>()
    var isCalibrating = false
        private set

    // Blink state machine
    private var wasBlinking = false
    private val recentBlinkTimestamps = ArrayDeque<Long>()

    // Saccade tracking
    private var lastGazeX = 0.5f
    private var lastGazeY = 0.5f
    private val recentSaccadeTimestamps = ArrayDeque<Long>()

    // Anomalies
    private val activeAnomalies = ArrayDeque<AnomalyAlert>()
    private var lastAnomalyTimeMs = mutableMapOf<AnomalyType, Long>()

    // Smoothed metrics
    private var smoothedScore = 95f
    private var smoothedStress = 15f
    private var smoothedCognitive = 20f
    private var smoothedHesitation = 10f

    fun startCalibration() {
        calibrationSamples.clear()
        isCalibrating = true
    }

    fun reset() {
        history.clear()
        scoreHistory.clear()
        recentBlinkTimestamps.clear()
        recentSaccadeTimestamps.clear()
        activeAnomalies.clear()
        lastAnomalyTimeMs.clear()
        smoothedScore = 95f
    }

    fun process(metrics: RawFeatureMetrics, fps: Int): BehavioralIndicators {
        if (!metrics.isFacePresent) {
            return BehavioralIndicators(
                truthfulnessScore = smoothedScore.toInt(),
                stressLevel = smoothedStress.toInt(),
                cognitiveLoad = smoothedCognitive.toInt(),
                hesitationScore = smoothedHesitation.toInt(),
                isFacePresent = false,
                fps = fps,
                timelineHistory = scoreHistory.toList()
            )
        }

        val now = metrics.timestampMs

        // Handle active calibration
        if (isCalibrating) {
            calibrationSamples.add(metrics)
            if (calibrationSamples.size >= 90) { // ~3 seconds at 30 fps
                val avgEar = calibrationSamples.map { it.avgEar }.average().toFloat()
                val avgBrow = calibrationSamples.map { it.browFurrowDist }.average().toFloat()
                val avgLip = calibrationSamples.map { it.lipCompressionRatio }.average().toFloat()
                val avgPitch = calibrationSamples.map { it.pitch }.average().toFloat()
                val avgYaw = calibrationSamples.map { it.yaw }.average().toFloat()

                calibrationData = CalibrationData(
                    isCalibrated = true,
                    baselineEar = avgEar,
                    baselineBrowDist = avgBrow,
                    baselineLipRatio = avgLip,
                    baselinePitch = avgPitch,
                    baselineYaw = avgYaw
                )
                isCalibrating = false
            }
        }

        // Add to sliding window
        history.addLast(metrics)
        while (history.isNotEmpty() && now - history.first.timestampMs > windowMs) {
            history.removeFirst()
        }

        // 1. Blink Detection & Rate Tracking
        if (!wasBlinking && metrics.isBlinking) {
            recentBlinkTimestamps.addLast(now)
        }
        wasBlinking = metrics.isBlinking

        // Prune blinks older than 20 seconds
        while (recentBlinkTimestamps.isNotEmpty() && now - recentBlinkTimestamps.first > 20_000L) {
            recentBlinkTimestamps.removeFirst()
        }
        val bpm = if (recentBlinkTimestamps.size > 1) {
            (recentBlinkTimestamps.size / 20f) * 60f
        } else {
            14f
        }

        // 2. Saccade / Rapid Eye Darting Detection
        val gazeDelta = abs(metrics.gazeX - lastGazeX) + abs(metrics.gazeY - lastGazeY)
        if (gazeDelta > 0.18f) {
            recentSaccadeTimestamps.addLast(now)
        }
        lastGazeX = metrics.gazeX
        lastGazeY = metrics.gazeY

        while (recentSaccadeTimestamps.isNotEmpty() && now - recentSaccadeTimestamps.first > 4000L) {
            recentSaccadeTimestamps.removeFirst()
        }
        val saccadesPerSec = recentSaccadeTimestamps.size / 4.0f

        // 3. Anomaly Rule Engine
        detectAnomalies(metrics, now, bpm, saccadesPerSec)

        // Prune old anomalies (older than 6 seconds)
        while (activeAnomalies.isNotEmpty() && now - activeAnomalies.first.timestampMs > 6000L) {
            activeAnomalies.removeFirst()
        }

        // 4. Score Aggregation
        var rawScore = 96f
        var rawStress = 10f
        var rawCognitive = 18f
        var rawHesitation = 8f

        // Blink rate penalties
        if (bpm > 40f) {
            val penalty = ((bpm - 40f) * 0.8f).coerceAtMost(25f)
            rawScore -= penalty
            rawStress += penalty * 1.5f
        } else if (bpm < 4f && history.size > 60) {
            // Unusually suppressed blinking / cognitive fixation
            rawScore -= 12f
            rawCognitive += 20f
        }

        // Saccade penalties
        if (saccadesPerSec > 1.2f) {
            rawScore -= 15f
            rawHesitation += 25f
            rawStress += 15f
        }

        // Lip compression penalty
        val baselineLip = if (calibrationData.isCalibrated) calibrationData.baselineLipRatio else 0.14f
        if (metrics.lipCompressionRatio < baselineLip * 0.70f) {
            rawScore -= 16f
            rawHesitation += 30f
            rawStress += 20f
        }

        // Brow Furrow penalty
        val baselineBrow = if (calibrationData.isCalibrated) calibrationData.baselineBrowDist else 0.18f
        if (metrics.browFurrowDist < baselineBrow * 0.82f) {
            rawScore -= 14f
            rawCognitive += 25f
            rawStress += 15f
        }

        // Asymmetric smirk / micro-tension
        if (metrics.mouthAsymmetry > 0.045f) {
            rawScore -= 15f
            rawHesitation += 20f
        }

        // Active anomalies penalty multiplier
        val highSeverityCount = activeAnomalies.count { it.severity == AlertSeverity.HIGH }
        val medSeverityCount = activeAnomalies.count { it.severity == AlertSeverity.MEDIUM }
        rawScore -= (highSeverityCount * 14f + medSeverityCount * 7f)
        rawStress += (highSeverityCount * 18f + medSeverityCount * 9f)

        // Clamp targets
        rawScore = rawScore.coerceIn(10f, 99f)
        rawStress = rawStress.coerceIn(5f, 98f)
        rawCognitive = rawCognitive.coerceIn(5f, 98f)
        rawHesitation = rawHesitation.coerceIn(5f, 98f)

        // Exponential smoothing (smooth responsiveness)
        val alpha = 0.18f
        smoothedScore = alpha * rawScore + (1 - alpha) * smoothedScore
        smoothedStress = alpha * rawStress + (1 - alpha) * smoothedStress
        smoothedCognitive = alpha * rawCognitive + (1 - alpha) * smoothedCognitive
        smoothedHesitation = alpha * rawHesitation + (1 - alpha) * smoothedHesitation

        // Update score sparkline queue
        scoreHistory.addLast(smoothedScore)
        if (scoreHistory.size > 60) {
            scoreHistory.removeFirst()
        }

        // Dominant state classification
        val finalScore = smoothedScore.toInt()
        val dominantState = when {
            finalScore >= 85 -> BehavioralState.TRUTHFUL_CALM
            finalScore >= 70 && smoothedCognitive > smoothedStress -> BehavioralState.COGNITIVE_EFFORT
            finalScore >= 55 -> BehavioralState.MILD_TENSION
            finalScore >= 40 -> BehavioralState.HESITANT_SUPPRESSION
            else -> BehavioralState.HIGH_DECEPTION_RISK
        }

        return BehavioralIndicators(
            truthfulnessScore = finalScore,
            stressLevel = smoothedStress.toInt(),
            cognitiveLoad = smoothedCognitive.toInt(),
            hesitationScore = smoothedHesitation.toInt(),
            blinksPerMinute = bpm,
            saccadeFrequency = saccadesPerSec,
            dominantState = dominantState,
            recentAnomalies = activeAnomalies.toList().takeLast(4),
            isFacePresent = true,
            fps = fps,
            timelineHistory = scoreHistory.toList()
        )
    }

    private fun detectAnomalies(metrics: RawFeatureMetrics, now: Long, bpm: Float, saccadesPerSec: Float) {
        val cooldownMs = 2500L

        // 1. Rapid Blink Burst
        val blinksInLast15s = recentBlinkTimestamps.count { now - it < 1500L }
        if (blinksInLast15s >= 3 && canTrigger(AnomalyType.RAPID_BLINK_BURST, now, cooldownMs)) {
            triggerAnomaly(
                AnomalyType.RAPID_BLINK_BURST,
                "Rapid Blink Cluster",
                "3+ blinks within 1.5s indicates acute cognitive arousal or stress spike.",
                AlertSeverity.HIGH,
                now
            )
        }

        // 2. Prolonged Stare / Freeze
        if (history.size > 75 && (now - (recentBlinkTimestamps.lastOrNull() ?: 0L)) > 4500L &&
            canTrigger(AnomalyType.PROLONGED_STARE, now, 5000L)) {
            triggerAnomaly(
                AnomalyType.PROLONGED_STARE,
                "Prolonged Stare / Freeze",
                "No blinking for >4.5 seconds with fixated gaze (cognitive freeze or high focus).",
                AlertSeverity.MEDIUM,
                now
            )
        }

        // 3. Eye Saccade / Rapid Gaze Aversion
        if (saccadesPerSec >= 1.5f && canTrigger(AnomalyType.EYE_SACCADE, now, cooldownMs)) {
            triggerAnomaly(
                AnomalyType.EYE_SACCADE,
                "Gaze Aversion Saccade",
                "Erratic eye darting indicates visual search or avoidance behavior.",
                AlertSeverity.MEDIUM,
                now
            )
        }

        // 4. Lip Compression Spike
        val baselineLip = if (calibrationData.isCalibrated) calibrationData.baselineLipRatio else 0.14f
        if (metrics.lipCompressionRatio < baselineLip * 0.65f && canTrigger(AnomalyType.LIP_COMPRESSION, now, cooldownMs)) {
            triggerAnomaly(
                AnomalyType.LIP_COMPRESSION,
                "Lip Compression Spike",
                "Sharp thinning of lips indicates emotional suppression or withholding information.",
                AlertSeverity.HIGH,
                now
            )
        }

        // 5. Asymmetric Smirk / Micro-Tension
        if (metrics.mouthAsymmetry > 0.055f && canTrigger(AnomalyType.ASYMMETRIC_SMIRK, now, cooldownMs)) {
            triggerAnomaly(
                AnomalyType.ASYMMETRIC_SMIRK,
                "Asymmetric Tension (Smirk)",
                "Unilateral mouth elevation (AU12 asymmetry) suggests emotional discordance.",
                AlertSeverity.HIGH,
                now
            )
        }

        // 6. Brow Furrow Contraction
        val baselineBrow = if (calibrationData.isCalibrated) calibrationData.baselineBrowDist else 0.18f
        if (metrics.browFurrowDist < baselineBrow * 0.75f && canTrigger(AnomalyType.BROW_FURROW, now, cooldownMs)) {
            triggerAnomaly(
                AnomalyType.BROW_FURROW,
                "Brow Furrow Contraction",
                "Corrugator supercilii contraction (AU4) signals cognitive conflict or distress.",
                AlertSeverity.MEDIUM,
                now
            )
        }

        // 7. Micro-Aversion Head Jerk
        if (history.size >= 8) {
            val pastSample = history.toList().let { it.getOrNull(it.size - 8) }
            if (pastSample != null) {
                val yawDelta = abs(metrics.yaw - pastSample.yaw)
                val pitchDelta = abs(metrics.pitch - pastSample.pitch)
                if ((yawDelta > 16f || pitchDelta > 14f) && canTrigger(AnomalyType.HEAD_AVERSION, now, cooldownMs)) {
                    triggerAnomaly(
                        AnomalyType.HEAD_AVERSION,
                        "Head Micro-Aversion",
                        "Rapid orientation aversion jerk detected during response.",
                        AlertSeverity.HIGH,
                        now
                    )
                }
            }
        }
    }

    private fun canTrigger(type: AnomalyType, now: Long, cooldownMs: Long): Boolean {
        val lastTime = lastAnomalyTimeMs[type] ?: 0L
        return now - lastTime > cooldownMs
    }

    private fun triggerAnomaly(type: AnomalyType, title: String, desc: String, severity: AlertSeverity, now: Long) {
        lastAnomalyTimeMs[type] = now
        activeAnomalies.addLast(
            AnomalyAlert(
                type = type,
                title = title,
                description = desc,
                severity = severity,
                timestampMs = now
            )
        )
    }
}
