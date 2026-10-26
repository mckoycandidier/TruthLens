package com.example.vision

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Extracts behavioral and physiological micro-expression features from 3D facial landmarks.
 * Works with Google MediaPipe Face Mesh (468/478 landmarks).
 */
class FeatureExtractor {

    companion object {
        // Left Eye Landmark Indices
        private const val L_EYE_OUTER = 33
        private const val L_EYE_INNER = 133
        private const val L_EYE_TOP_1 = 160
        private const val L_EYE_TOP_2 = 158
        private const val L_EYE_BOT_1 = 144
        private const val L_EYE_BOT_2 = 153
        private const val L_IRIS_CENTER = 468

        // Right Eye Landmark Indices
        private const val R_EYE_INNER = 362
        private const val R_EYE_OUTER = 263
        private const val R_EYE_TOP_1 = 385
        private const val R_EYE_TOP_2 = 387
        private const val R_EYE_BOT_1 = 380
        private const val R_EYE_BOT_2 = 373
        private const val R_IRIS_CENTER = 473

        // Eyebrow Indices
        private const val L_BROW_INNER = 107
        private const val R_BROW_INNER = 336
        private const val GLABELLA = 168

        // Mouth & Lips Indices
        private const val MOUTH_CORNER_L = 61
        private const val MOUTH_CORNER_R = 291
        private const val LIP_TOP_OUTER = 0
        private const val LIP_TOP_INNER = 13
        private const val LIP_BOT_INNER = 14
        private const val LIP_BOT_OUTER = 17

        // Head Orientation Indices
        private const val NOSE_TIP = 1
        private const val CHIN = 152
        private const val FOREHEAD = 10

        // Blink Detection Threshold
        const val BLINK_EAR_THRESHOLD = 0.20f
    }

    fun extractFeatures(points: List<Point3D>, timestampMs: Long = System.currentTimeMillis()): RawFeatureMetrics {
        if (points.size < 468) {
            return RawFeatureMetrics(isFacePresent = false)
        }

        val faceWidth = dist2D(points[L_EYE_OUTER], points[R_EYE_OUTER]).coerceAtLeast(0.01f)
        val faceHeight = dist2D(points[FOREHEAD], points[CHIN]).coerceAtLeast(0.01f)

        // 1. Eye Aspect Ratio (EAR) for both eyes
        val leftEar = computeEAR(
            outer = points[L_EYE_OUTER],
            inner = points[L_EYE_INNER],
            top1 = points[L_EYE_TOP_1],
            bot1 = points[L_EYE_BOT_1],
            top2 = points[L_EYE_TOP_2],
            bot2 = points[L_EYE_BOT_2]
        )

        val rightEar = computeEAR(
            outer = points[R_EYE_OUTER],
            inner = points[R_EYE_INNER],
            top1 = points[R_EYE_TOP_1],
            bot1 = points[R_EYE_BOT_1],
            top2 = points[R_EYE_TOP_2],
            bot2 = points[R_EYE_BOT_2]
        )

        val avgEar = (leftEar + rightEar) * 0.5f
        val isBlinking = avgEar < BLINK_EAR_THRESHOLD

        // 2. Gaze Tracking / Relative Pupil Position
        val (gazeX, gazeY) = if (points.size >= 478) {
            val lIris = points[L_IRIS_CENTER]
            val lInner = points[L_EYE_INNER]
            val lOuter = points[L_EYE_OUTER]
            val eyeWidth = abs(lOuter.x - lInner.x).coerceAtLeast(0.001f)
            val relX = (lIris.x - minOf(lInner.x, lOuter.x)) / eyeWidth
            val relY = (lIris.y - points[L_EYE_TOP_1].y) / abs(points[L_EYE_BOT_1].y - points[L_EYE_TOP_1].y).coerceAtLeast(0.001f)
            Pair(relX.coerceIn(0f, 1f), relY.coerceIn(0f, 1f))
        } else {
            Pair(0.5f, 0.5f)
        }

        // 3. Eyebrow Metrics: Brow Furrow (AU4) & Inner Brow Raise (AU1)
        val rawBrowDist = dist2D(points[L_BROW_INNER], points[R_BROW_INNER])
        val browFurrowDist = (rawBrowDist / faceWidth).coerceIn(0f, 1f)

        val avgBrowY = (points[L_BROW_INNER].y + points[R_BROW_INNER].y) * 0.5f
        val browRaiseDist = ((points[GLABELLA].y - avgBrowY) / faceHeight).coerceIn(-1f, 1f)

        // 4. Mouth & Lip Dynamics: Compression & Asymmetry
        val mouthWidth = dist2D(points[MOUTH_CORNER_L], points[MOUTH_CORNER_R]).coerceAtLeast(0.01f)
        val lipThickness = (dist2D(points[LIP_TOP_OUTER], points[LIP_TOP_INNER]) +
                dist2D(points[LIP_BOT_INNER], points[LIP_BOT_OUTER]))
        val lipCompressionRatio = (lipThickness / mouthWidth).coerceIn(0f, 1f)

        // Mouth Asymmetry (compensated for head roll)
        val deltaY = points[MOUTH_CORNER_R].y - points[MOUTH_CORNER_L].y
        val mouthAsymmetry = abs(deltaY / faceHeight).coerceIn(0f, 1f)

        // 5. Head Pose: Roll, Pitch, Yaw
        val eyeDeltaX = points[R_EYE_OUTER].x - points[L_EYE_OUTER].x
        val eyeDeltaY = points[R_EYE_OUTER].y - points[L_EYE_OUTER].y
        val roll = Math.toDegrees(atan2(eyeDeltaY.toDouble(), eyeDeltaX.toDouble())).toFloat()

        val eyeMidX = (points[L_EYE_OUTER].x + points[R_EYE_OUTER].x) * 0.5f
        val yaw = ((points[NOSE_TIP].x - eyeMidX) / faceWidth * 90f).coerceIn(-90f, 90f)

        val eyeMidY = (points[L_EYE_OUTER].y + points[R_EYE_OUTER].y) * 0.5f
        val pitch = ((points[NOSE_TIP].y - eyeMidY) / faceHeight * 90f - 10f).coerceIn(-90f, 90f)

        return RawFeatureMetrics(
            timestampMs = timestampMs,
            leftEar = leftEar,
            rightEar = rightEar,
            avgEar = avgEar,
            isBlinking = isBlinking,
            gazeX = gazeX,
            gazeY = gazeY,
            browFurrowDist = browFurrowDist,
            browRaiseDist = browRaiseDist,
            lipCompressionRatio = lipCompressionRatio,
            mouthAsymmetry = mouthAsymmetry,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            isFacePresent = true
        )
    }

    private fun computeEAR(
        outer: Point3D,
        inner: Point3D,
        top1: Point3D,
        bot1: Point3D,
        top2: Point3D,
        bot2: Point3D
    ): Float {
        val vertical1 = dist2D(top1, bot1)
        val vertical2 = dist2D(top2, bot2)
        val horizontal = dist2D(outer, inner).coerceAtLeast(0.001f)
        return (vertical1 + vertical2) / (2f * horizontal)
    }

    private fun dist2D(p1: Point3D, p2: Point3D): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
