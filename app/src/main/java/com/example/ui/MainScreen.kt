package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceAlpha
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vision.FaceMeshAnalyzer
import java.util.concurrent.Executors

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isFrontCamera by remember { mutableStateOf(true) }
    var showEthicsDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    val analyzer = remember(context) {
        FaceMeshAnalyzer(context, isFrontCamera = isFrontCamera)
    }

    LaunchedEffect(isFrontCamera) {
        analyzer.isFrontCamera = isFrontCamera
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
        }
    }

    val meshResult by analyzer.meshResult.collectAsState()
    val rawMetrics by analyzer.rawFeatures.collectAsState()
    val indicators by analyzer.behavioralMetrics.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CyberBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                // Live CameraX Feed
                CameraPreviewView(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    isFrontCamera = isFrontCamera,
                    analyzer = analyzer,
                    modifier = Modifier.fillMaxSize()
                )

                // Biometric Mesh Overlay Canvas
                BiometricOverlay(
                    mesh = meshResult,
                    metrics = rawMetrics,
                    truthfulnessScore = indicators.truthfulnessScore,
                    modifier = Modifier.fillMaxSize()
                )

                // Top HUD Bar
                TopHudBar(
                    fps = indicators.fps,
                    isFaceDetected = indicators.isFacePresent,
                    onSwitchCamera = { isFrontCamera = !isFrontCamera },
                    onStartCalibration = {
                        analyzer.startCalibration()
                        showCalibrationDialog = true
                    },
                    onOpenEthics = { showEthicsDialog = true },
                    onResetSession = { analyzer.resetSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )

                // Bottom Metrics Dashboard
                MetricsDashboard(
                    indicators = indicators,
                    rawMetrics = rawMetrics,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

            } else {
                // Camera Permission Required UI
                CameraPermissionScreen(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            // Ethics & Scientific Disclaimer Dialog
            if (showEthicsDialog) {
                EthicsDisclaimerDialog(onDismiss = { showEthicsDialog = false })
            }

            // Baseline Calibration Modal
            if (showCalibrationDialog) {
                CalibrationProgressDialog(
                    isCalibrating = analyzer.truthfulnessScorer.isCalibrating,
                    onComplete = { showCalibrationDialog = false }
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewView(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isFrontCamera: Boolean,
    analyzer: FaceMeshAnalyzer,
    modifier: Modifier = Modifier
) {
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(
                    cameraProvider = cameraProvider,
                    previewView = previewView,
                    lifecycleOwner = lifecycleOwner,
                    isFrontCamera = isFrontCamera,
                    analyzer = analyzer,
                    executor = cameraExecutor
                )
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(
                    cameraProvider = cameraProvider,
                    previewView = previewView,
                    lifecycleOwner = lifecycleOwner,
                    isFrontCamera = isFrontCamera,
                    analyzer = analyzer,
                    executor = cameraExecutor
                )
            }, ContextCompat.getMainExecutor(previewView.context))
        },
        modifier = modifier
    )
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isFrontCamera: Boolean,
    analyzer: FaceMeshAnalyzer,
    executor: java.util.concurrent.ExecutorService
) {
    try {
        cameraProvider.unbindAll()

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(executor, analyzer)
            }

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    } catch (e: Exception) {
        Log.e("CameraPreviewView", "Binding camera failed: ${e.message}", e)
    }
}

@Composable
private fun TopHudBar(
    fps: Int,
    isFaceDetected: Boolean,
    onSwitchCamera: () -> Unit,
    onStartCalibration: () -> Unit,
    onOpenEthics: () -> Unit,
    onResetSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurfaceAlpha)
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title & Tracking Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isFaceDetected) NeonEmerald else AlertAmber)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "TRUTHLENS AI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
                Text(
                    text = if (isFaceDetected) "478 3D LANDMARKS" else "SCANNING FOR FACE...",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isFaceDetected) NeonCyan else TextMuted
                )
            }
        }

        // FPS Badge & Controls
        Row(verticalAlignment = Alignment.CenterVertically) {
            // FPS badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberSurface)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$fps FPS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (fps >= 25) NeonEmerald else AlertAmber
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Calibration button
            IconButton(
                onClick = onStartCalibration,
                modifier = Modifier.size(36.dp).testTag("calibrate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Calibrate Baseline",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Flip camera button
            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier.size(36.dp).testTag("flip_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Ethics disclaimer button
            IconButton(
                onClick = onOpenEthics,
                modifier = Modifier.size(36.dp).testTag("ethics_info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Scientific & Ethics Disclaimer",
                    tint = AlertAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EthicsDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = AlertAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ethics & Scientific Overview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "CRITICAL OPERATIONAL NOTICE:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DangerCrimson,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TruthLens measures physiological tension, autonomic arousal, micro-gestures, and cognitive load indicators—NOT objective 'truth'. No computer vision system or polygraph can determine absolute veracity.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "What is being measured?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Eye Dynamics: Blink frequency spikes (BPM) and rapid saccades (gaze aversion).\n" +
                            "• Facial Micro-Tension: Lip compression (orbicularis oris tightening, AU24) and asymmetric smiles (AU12 asymmetry).\n" +
                            "• Eyebrow Corrugator: Inner brow furrowing (AU4) associated with acute cognitive strain.\n" +
                            "• Head Pose: Sudden micro-aversions and tremor variations.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Confounding Factors:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Elevated stress signals can be caused by social anxiety, dry eyes, neurodivergence, fatigue, or challenging questions. Always calibrate baseline and never use for punitive or discriminatory decisions.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "100% On-Device Privacy:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All computer vision inference and landmark tracking runs strictly locally using MediaPipe on your hardware. Zero video frames or biometric telemetry ever leave the device.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.testTag("dismiss_ethics_button")
            ) {
                Text(text = "I Understand & Agree", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun CalibrationProgressDialog(
    isCalibrating: Boolean,
    onComplete: () -> Unit
) {
    LaunchedEffect(isCalibrating) {
        if (!isCalibrating) {
            onComplete()
        }
    }

    AlertDialog(
        onDismissRequest = { /* Keep locked during 3s calibration */ },
        title = {
            Text(
                text = "Resting Baseline Calibration",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                CircularProgressIndicator(
                    color = NeonCyan,
                    trackColor = CyberSurfaceVariant,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Look directly at the front camera with a relaxed, neutral expression for 3 seconds.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Calibrating resting blink rate, brow separation, and lip thickness...",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {},
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun CameraPermissionScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CyberSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Camera Access Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "TruthLens requires real-time camera access to extract 478 3D facial landmarks and analyze micro-expressions. All analysis is computed entirely on-device for maximum privacy.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_camera_permission_button")
            ) {
                Text(
                    text = "Grant Camera Permission",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
