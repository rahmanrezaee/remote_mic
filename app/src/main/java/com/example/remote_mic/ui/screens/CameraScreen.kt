package com.example.remote_mic.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.video.Quality
import com.example.remote_mic.AppState
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    isRecording: Boolean,
    cameraManager: CameraManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    appState: AppState,
    connectionManager: ConnectionManager
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraStatus by remember { mutableStateOf("Initializing...") }
    var recordingTime by remember { mutableStateOf("00:00") }
    var showQualitySelector by remember { mutableStateOf(false) }

    // Setup camera callbacks
    LaunchedEffect(cameraManager) {
        cameraManager.onCameraStatusChanged = { status ->
            cameraStatus = status
        }
        cameraManager.onRecordingTimeChanged = { time ->
            recordingTime = time
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview with Enhanced Overlay
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraManager.initializeCamera(this, lifecycleOwner)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Enhanced gradient overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }

        // Enhanced Top Status Bar
        EnhancedTopStatusBar(
            isRecording = isRecording,
            cameraManager = cameraManager,
            cameraStatus = cameraStatus,
            recordingTime = recordingTime,
            isConnected = appState.isConnected,
            onSettingsClick = { showQualitySelector = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Enhanced Recording Controls
        EnhancedRecordingControls(
            isRecording = isRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onCameraSwitch = { cameraManager.switchCamera() },
            onGalleryClick = { /* Open gallery */ },
            canSwitchCamera = cameraManager.canSwitchCamera(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Enhanced Side Controls
        EnhancedSideControls(
            cameraManager = cameraManager,
            isRecording = isRecording,
            onQualityClick = { showQualitySelector = true },
            modifier = Modifier.align(Alignment.CenterEnd),
            recordingTime = recordingTime
        )



        // Quality Selector Dialog
        if (showQualitySelector) {
            QualitySelector(
                currentQuality = cameraManager.getCurrentQuality(),
                onQualitySelected = { quality ->
                    cameraManager.setVideoQuality(quality)
                    showQualitySelector = false
                },
                onDismiss = { showQualitySelector = false }
            )
        }
    }
}

// Reusable Card Component for Control Elements
@Composable
private fun ControlCard(
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled) { onClick() }
            } else {
                Modifier
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

// Flash Control Component
@Composable
private fun FlashControl(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    if (cameraManager.hasFlashUnit()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = modifier
        ) {
            IconButton(
                onClick = { cameraManager.toggleFlash() },
                modifier = Modifier.padding(8.dp)
            ) {
                val flashIcon = when (cameraManager.getCurrentFlashMode()) {
                    androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                }

                Icon(
                    imageVector = flashIcon,
                    contentDescription = "Flash Mode",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Quality Display Component
@Composable
private fun QualityDisplay(
    cameraManager: CameraManager,
    onClick: () -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    ControlCard(
        onClick = onClick,
        enabled = !isRecording,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.HighQuality,
            contentDescription = "Video Quality",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = cameraManager.getQualityText(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Quality",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

// Timer Display Component
@Composable
private fun TimerDisplay(
    recordingTime: String,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    ControlCard(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = "Recording Timer",
            tint = if (isRecording) Color.Red else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = recordingTime,
            style = MaterialTheme.typography.labelSmall,
            color = if (isRecording) Color.Red else Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EnhancedSideControls(
    cameraManager: CameraManager,
    recordingTime: String,
    isRecording: Boolean,
    onQualityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quality indicator
        QualityDisplay(
            cameraManager = cameraManager,
            onClick = onQualityClick,
            isRecording = isRecording
        )

        // Flash control
        FlashControl(cameraManager = cameraManager)



    }
}

@Composable
private fun EnhancedTopStatusBar(
    isRecording: Boolean,
    cameraStatus: String,
    isConnected: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraManager: CameraManager,
    recordingTime: String
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enhanced recording status
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated recording indicator
                AnimatedRecordingDot(isRecording = isRecording)

                Column {
                    Text(
                        text = if (isRecording) "RECORDING" else "READY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = cameraStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Show timer when recording in top right, otherwise show connection status
            if (isRecording) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Recording Timer",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = recordingTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Connection Status",
                        tint = if (isConnected) Color.Green else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )

                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(
    recordingTime: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_indicator")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicatorAlpha"
    )

    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Red.copy(alpha = alpha)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
            Text(
                text = "REC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = recordingTime,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun QualitySelector(
    currentQuality: Quality,
    onQualitySelected: (Quality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Video Quality",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val qualities = listOf(
                    Quality.UHD to "4K Ultra HD (2160p)",
                    Quality.FHD to "Full HD (1080p)",
                    Quality.HD to "HD (720p)",
                    Quality.SD to "Standard (480p)"
                )

                qualities.forEach { (quality, label) ->
                    QualityOption(
                        quality = quality,
                        label = label,
                        isSelected = quality == currentQuality,
                        onClick = { onQualitySelected(quality) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QualityOption(
    quality: Quality,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = getQualityDescription(quality),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getQualityDescription(quality: Quality): String {
    return when (quality) {
        Quality.UHD -> "Best quality, larger file size"
        Quality.FHD -> "High quality, balanced file size"
        Quality.HD -> "Good quality, moderate file size"
        Quality.SD -> "Basic quality, smallest file size"
        else -> "Auto quality selection"
    }
}

@Composable
private fun AnimatedRecordingDot(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale)
            .background(
                if (isRecording) Color.Red.copy(alpha = alpha) else Color.Gray,
                CircleShape
            )
    )
}

@Composable
private fun EnhancedRecordingControls(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCameraSwitch: () -> Unit,
    onGalleryClick: () -> Unit,
    canSwitchCamera: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera flip button
            EnhancedControlButton(
                icon = Icons.Default.Cameraswitch,
                size = 56.dp,
                backgroundColor = Color.White.copy(alpha = if (canSwitchCamera) 0.2f else 0.1f),
                iconColor = Color.White.copy(alpha = if (canSwitchCamera) 1f else 0.5f),
                enabled = canSwitchCamera && !isRecording,
                onClick = onCameraSwitch
            )

            // Main record button with enhanced animation
            MainRecordButton(
                isRecording = isRecording,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )

            // Gallery button
            EnhancedControlButton(
                icon = Icons.Default.PhotoLibrary,
                size = 56.dp,
                backgroundColor = Color.White.copy(alpha = 0.2f),
                iconColor = Color.White,
                enabled = true,
                onClick = onGalleryClick
            )
        }
    }
}

@Composable
private fun MainRecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "record_button")

    val buttonScale by animateFloatAsState(
        targetValue = if (isRecording) 0.9f else 1f,
        animationSpec = tween(150),
        label = "buttonScale"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ring when recording
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .background(
                        Color.Red.copy(alpha = 0.3f),
                        CircleShape
                    )
            )
        }

        // Main button
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(buttonScale)
                .background(
                    if (isRecording) Color.Red else Color.White,
                    CircleShape
                )
                .border(
                    4.dp,
                    Color.White.copy(alpha = 0.6f),
                    CircleShape
                )
                .clickable {
                    if (isRecording) onStopRecording() else onStartRecording()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = if (isRecording) "Stop" else "Record",
                tint = if (isRecording) Color.White else Color.Red,
                modifier = Modifier.size(if (isRecording) 32.dp else 40.dp)
            )
        }
    }
}

@Composable
private fun EnhancedControlButton(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    backgroundColor: Color,
    iconColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "controlScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .clickable(enabled = enabled) {
                if (enabled) {
                    isPressed = true
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}