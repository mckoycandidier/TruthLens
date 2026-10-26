package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CameraX ImageAnalysis analyzer running on-device MediaPipe Face Landmarker.
 * Feeds 478 3D landmarks into FeatureExtractor and TruthfulnessScorer in real time.
 */
class FaceMeshAnalyzer(
    private val context: Context,
    var isFrontCamera: Boolean = true
) : ImageAnalysis.Analyzer {

    private val tag = "FaceMeshAnalyzer"
    private var faceLandmarker: FaceLandmarker? = null

    private val featureExtractor = FeatureExtractor()
    val truthfulnessScorer = TruthfulnessScorer()

    // Flow states for Jetpack Compose
    private val _meshResult = MutableStateFlow(FaceMeshResult())
    val meshResult = _meshResult.asStateFlow()

    private val _behavioralMetrics = MutableStateFlow(BehavioralIndicators())
    val behavioralMetrics = _behavioralMetrics.asStateFlow()

    private val _rawFeatures = MutableStateFlow(RawFeatureMetrics())
    val rawFeatures = _rawFeatures.asStateFlow()

    // FPS Counter
    private var frameCount = 0
    private var lastFpsTimestamp = SystemClock.uptimeMillis()
    private var currentFps = 0

    // Frame throttling to maintain smooth 30 FPS without queuing
    private var isProcessing = false

    init {
        initLandmarker()
    }

    private fun initLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .setResultListener { result: FaceLandmarkerResult, _: MPImage ->
                    handleLandmarkerResult(result)
                    isProcessing = false
                }
                .setErrorListener { error: RuntimeException ->
                    Log.e(tag, "MediaPipe Landmarker error: ${error.message}", error)
                    isProcessing = false
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(tag, "FaceLandmarker successfully initialized")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize FaceLandmarker: ${e.message}", e)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.uptimeMillis()

        // Measure FPS
        frameCount++
        if (now - lastFpsTimestamp >= 1000L) {
            currentFps = frameCount
            frameCount = 0
            lastFpsTimestamp = now
        }

        if (faceLandmarker == null || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val startInferenceTime = SystemClock.uptimeMillis()

        try {
            val rawBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f) // Mirror for natural selfie orientation
                }
            }

            val processedBitmap = Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )

            val mpImage = BitmapImageBuilder(processedBitmap).build()
            faceLandmarker?.detectAsync(mpImage, now)

        } catch (e: Exception) {
            Log.e(tag, "Error analyzing image frame: ${e.message}", e)
            isProcessing = false
        } finally {
            imageProxy.close()
        }
    }

    private fun handleLandmarkerResult(result: FaceLandmarkerResult) {
        val landmarksList = result.faceLandmarks()
        if (landmarksList.isNullOrEmpty() || landmarksList[0].isEmpty()) {
            _meshResult.value = FaceMeshResult(hasFace = false)
            val updatedMetrics = truthfulnessScorer.process(
                RawFeatureMetrics(isFacePresent = false),
                currentFps
            )
            _behavioralMetrics.value = updatedMetrics
            return
        }

        val rawLandmarks = landmarksList[0]
        val points = rawLandmarks.map {
            Point3D(it.x(), it.y(), it.z())
        }

        // Calculate bounding box in normalized space
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val boundingBox = RectF(minX, minY, maxX, maxY)

        val mesh = FaceMeshResult(
            points = points,
            hasFace = true,
            boundingBox = boundingBox,
            imageWidth = 640,
            imageHeight = 480
        )
        _meshResult.value = mesh

        // Extract micro-expression features
        val nowMs = System.currentTimeMillis()
        val rawMetrics = featureExtractor.extractFeatures(points, nowMs)
        _rawFeatures.value = rawMetrics

        // Compute behavioral and composure indicators
        val indicators = truthfulnessScorer.process(rawMetrics, currentFps)
        _behavioralMetrics.value = indicators
    }

    fun startCalibration() {
        truthfulnessScorer.startCalibration()
    }

    fun resetSession() {
        truthfulnessScorer.reset()
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}
