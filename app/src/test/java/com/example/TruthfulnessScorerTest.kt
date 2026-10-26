package com.example

import com.example.vision.AlertSeverity
import com.example.vision.AnomalyType
import com.example.vision.BehavioralState
import com.example.vision.FeatureExtractor
import com.example.vision.Point3D
import com.example.vision.RawFeatureMetrics
import com.example.vision.TruthfulnessScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TruthfulnessScorerTest {

    private lateinit var scorer: TruthfulnessScorer
    private lateinit var extractor: FeatureExtractor

    @Before
    fun setUp() {
        scorer = TruthfulnessScorer()
        extractor = FeatureExtractor()
    }

    @Test
    fun testRestingFace_ProducesHighTruthfulnessScore() {
        val normalMetrics = RawFeatureMetrics(
            timestampMs = 1000L,
            leftEar = 0.28f,
            rightEar = 0.28f,
            avgEar = 0.28f,
            isBlinking = false,
            gazeX = 0.5f,
            gazeY = 0.5f,
            browFurrowDist = 0.18f,
            lipCompressionRatio = 0.15f,
            mouthAsymmetry = 0.01f,
            isFacePresent = true
        )

        val indicators = scorer.process(normalMetrics, fps = 30)

        assertTrue(indicators.isFacePresent)
        assertTrue("Resting score should be >= 80", indicators.truthfulnessScore >= 80)
        assertEquals(BehavioralState.TRUTHFUL_CALM, indicators.dominantState)
    }

    @Test
    fun testRapidBlinkBurst_TriggersAnomalyAlert() {
        val baseTime = 10_000L

        // Simulate 4 rapid blinks within 1.2 seconds
        var indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime, avgEar = 0.12f, isBlinking = true, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 100L, avgEar = 0.28f, isBlinking = false, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 300L, avgEar = 0.12f, isBlinking = true, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 400L, avgEar = 0.28f, isBlinking = false, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 700L, avgEar = 0.12f, isBlinking = true, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 800L, avgEar = 0.28f, isBlinking = false, isFacePresent = true),
            fps = 30
        )
        indicators = scorer.process(
            RawFeatureMetrics(timestampMs = baseTime + 1100L, avgEar = 0.12f, isBlinking = true, isFacePresent = true),
            fps = 30
        )

        val hasRapidBlink = indicators.recentAnomalies.any { it.type == AnomalyType.RAPID_BLINK_BURST }
        assertTrue("Rapid blink burst should be detected", hasRapidBlink)
    }

    @Test
    fun testLipCompression_TriggersCompressionAlert() {
        val now = 20_000L
        val compressedMetrics = RawFeatureMetrics(
            timestampMs = now,
            avgEar = 0.28f,
            browFurrowDist = 0.18f,
            lipCompressionRatio = 0.05f, // severely compressed lips
            isFacePresent = true
        )

        val indicators = scorer.process(compressedMetrics, fps = 30)

        val hasLipCompression = indicators.recentAnomalies.any { it.type == AnomalyType.LIP_COMPRESSION }
        assertTrue("Lip compression anomaly should be triggered", hasLipCompression)
    }

    @Test
    fun testFeatureExtractor_WithEmptyLandmarks_ReturnsEmptyMetrics() {
        val emptyPoints = emptyList<Point3D>()
        val result = extractor.extractFeatures(emptyPoints)

        assertFalse(result.isFacePresent)
        assertEquals(0f, result.avgEar, 0.001f)
    }
}
