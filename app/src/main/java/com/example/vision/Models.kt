package com.example.vision

import android.graphics.RectF

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class FaceMeshResult(
    val points: List<Point3D> = emptyList(),
    val hasFace: Boolean = false,
    val boundingBox: RectF? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val inferenceTimeMs: Long = 0L
)

data class RawFeatureMetrics(
    val timestampMs: Long = System.currentTimeMillis(),
    val leftEar: Float = 0f,
    val rightEar: Float = 0f,
    val avgEar: Float = 0f,
    val isBlinking: Boolean = false,
    val gazeX: Float = 0.5f,
    val gazeY: Float = 0.5f,
    val browFurrowDist: Float = 0f,
    val browRaiseDist: Float = 0f,
    val lipCompressionRatio: Float = 0f,
    val mouthAsymmetry: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val isFacePresent: Boolean = false
)

enum class BehavioralState(val label: String, val description: String) {
    TRUTHFUL_CALM("Relaxed & Congruent", "Normal blink rhythm, steady gaze, relaxed facial musculature."),
    COGNITIVE_EFFORT("Cognitive Focus", "Moderate brow focus, normal saccades during recall or thinking."),
    MILD_TENSION("Elevated Tension", "Increased blink rate, minor jaw/lip clenching detected."),
    HESITANT_SUPPRESSION("Micro-Suppression", "Lip compression spikes and brief gaze aversions detected."),
    HIGH_DECEPTION_RISK("High Stress / Incongruence", "Rapid blink burst, asymmetric micro-tension, micro-aversions.")
}

enum class AlertSeverity {
    LOW, MEDIUM, HIGH
}

enum class AnomalyType(val displayName: String) {
    RAPID_BLINK_BURST("Rapid Blink Burst"),
    PROLONGED_STARE("Prolonged Stare / Freeze"),
    EYE_SACCADE("Rapid Gaze Aversion"),
    LIP_COMPRESSION("Lip Compression Spike"),
    ASYMMETRIC_SMIRK("Asymmetric Micro-Tension"),
    BROW_FURROW("Brow Furrow Contraction"),
    HEAD_AVERSION("Micro-Aversion Head Jerk")
}

data class AnomalyAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val type: AnomalyType,
    val title: String,
    val description: String,
    val severity: AlertSeverity
)

data class CalibrationData(
    val isCalibrated: Boolean = false,
    val baselineEar: Float = 0.28f,
    val baselineBrowDist: Float = 0.18f,
    val baselineLipRatio: Float = 0.15f,
    val baselinePitch: Float = 0f,
    val baselineYaw: Float = 0f
)

data class BehavioralIndicators(
    val truthfulnessScore: Int = 95,
    val stressLevel: Int = 12,
    val cognitiveLoad: Int = 20,
    val hesitationScore: Int = 10,
    val blinksPerMinute: Float = 16f,
    val saccadeFrequency: Float = 0.2f,
    val dominantState: BehavioralState = BehavioralState.TRUTHFUL_CALM,
    val recentAnomalies: List<AnomalyAlert> = emptyList(),
    val isFacePresent: Boolean = false,
    val fps: Int = 0,
    val timelineHistory: List<Float> = emptyList() // Last 60 score data points for sparkline
)
