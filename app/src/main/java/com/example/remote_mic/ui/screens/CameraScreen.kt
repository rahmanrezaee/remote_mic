package com.example.remote_mic.ui.screens

import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.remote_mic.AppState
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.components.UnifiedEnhancedStatusBar
import com.example.remote_mic.utils.formatFileSize
import kotlinx.coroutines.delay
import java.io.File

// Updated CameraScreen composable
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
    var showRoleSwitchDialog by remember { mutableStateOf(false) }
// Add state for file received dialog
    var showFileReceivedDialog by remember { mutableStateOf(false) }


    LaunchedEffect(appState.receivedAudioFile) {
        if (appState.receivedAudioFile != null && appState.myRole == "camera") {
            showFileReceivedDialog = true
        }
    }


    // Check if we can show merge button
    val canShowMergeButton = appState.receivedAudioFile != null &&
            appState.recordedVideoFile != null

    // Setup camera callbacks
    LaunchedEffect(cameraManager) {
        cameraManager.onCameraStatusChanged = { status ->
            cameraStatus = status
        }
        cameraManager.onRecordingTimeChanged = { time ->
            recordingTime = time
        }
        cameraManager.onVideoFileReady = { videoFile ->
            connectionManager.setVideoFile(videoFile)
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

        // Enhanced Top Status Bar with Role Info
        UnifiedEnhancedStatusBar(
            appState = appState,
            connectionManager = connectionManager,
            isRecording = isRecording,
            recordingTime = recordingTime,
            cameraManager = cameraManager,
            modifier = Modifier.align(Alignment.TopCenter)
        )




        // Enhanced Recording Controls
        EnhancedRecordingControls(
            isRecording = isRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onCameraSwitch = { cameraManager.switchCamera() },
            // Remove the merge button completely - no onMergeClick parameter
            canSwitchCamera = cameraManager.canSwitchCamera(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Enhanced Side Controls
        EnhancedSideControls(
            cameraManager = cameraManager,
            isRecording = isRecording,
            appState = appState,
            connectionManager = connectionManager,
            onQualityClick = { showQualitySelector = true },
            modifier = Modifier.align(Alignment.CenterEnd),
            recordingTime = recordingTime
        )

//        // Role Switch Floating Button


        // Role Switch Card (Alternative UI - uncomment to use instead of floating button)
        /*
        RoleSwitchCard(
            appState = appState,
            connectionManager = connectionManager,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .offset(y = 80.dp)
        )
        */

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

        // Role Switch Confirmation Dialog
        if (showRoleSwitchDialog) {
            RoleSwitchConfirmationDialog(
                currentRole = appState.myRole,
                onConfirm = {
                    connectionManager.switchRoles()
                    showRoleSwitchDialog = false
                },
                onDismiss = { showRoleSwitchDialog = false }
            )
        }

        if (showFileReceivedDialog) {
            FileReceivedDialog(
                receivedFile = appState.receivedAudioFile,
                onContinueEditing = {
                    showFileReceivedDialog = false
                    // Navigate to merge/editing screen
                    connectionManager.updateState { copy(showMergeScreen = true) }
                },
                onDiscard = {
                    showFileReceivedDialog = false
                    // Clear the received file
                    connectionManager.discardReceivedFile()
                },
                onDismiss = {
                    showFileReceivedDialog = false
                }
            )
        }
    }
}
// Updated EnhancedRecordingControls with Timer
@Composable
private fun EnhancedRecordingControls(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCameraSwitch: () -> Unit,
    canSwitchCamera: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp).background(color =  Color.Transparent)
        ,
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
                backgroundColor = Color.White.copy(alpha = if (canSwitchCamera && !isRecording) 0.2f else 0.1f),
                iconColor = Color.White.copy(alpha = if (canSwitchCamera && !isRecording) 1f else 0.5f),
                enabled = canSwitchCamera && !isRecording,
                onClick = onCameraSwitch
            )

            // Main record button with enhanced animation
            MainRecordButton(
                isRecording = isRecording,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )

            // Timer control (replaces merge button)
            TimerControl(
                onTimerSelected = { seconds ->
                    if (seconds == 0) {
                        onStartRecording() // Start immediately
                    }
                    // For other values, the countdown handles the delay
                },
                isRecording = isRecording
            )
        }
    }
}
@Composable
fun FileReceivedDialog(
    receivedFile: File?,
    onContinueEditing: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    if (receivedFile == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = "Audio File Received",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Audio File Received",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // File info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = receivedFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Size: ${formatFileSize(receivedFile.length())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Received from microphone device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "What would you like to do with this audio file?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Compact buttons in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Discard button
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Discard",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Continue Editing button
                    Button(
                        onClick = onContinueEditing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {}, // Empty since buttons are in the text section
        dismissButton = {} // Empty since buttons are in the text section
    )
}




// Timer Control Component to replace merge button
@Composable
private fun TimerControl(
    onTimerSelected: (Int) -> Unit,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    var showTimerOptions by remember { mutableStateOf(false) }
    var selectedTimer by remember { mutableStateOf(0) } // 0 means no timer
    var countdown by remember { mutableStateOf(0) }
    var isCountingDown by remember { mutableStateOf(false) }

    // Countdown effect
    LaunchedEffect(isCountingDown, countdown) {
        if (isCountingDown && countdown > 0) {
            delay(1000)
            countdown--
        } else if (isCountingDown && countdown == 0) {
            isCountingDown = false
            onTimerSelected(0) // Trigger recording start
        }
    }

    if (showTimerOptions) {
        TimerSelectionDialog(
            onTimerSelected = { seconds ->
                selectedTimer = seconds
                if (seconds > 0) {
                    countdown = seconds
                    isCountingDown = true
                }
                showTimerOptions = false
            },
            onDismiss = { showTimerOptions = false }
        )
    }

    // Timer button
    Box(
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCountingDown) {
            // Show countdown
            TimerCountdownDisplay(countdown = countdown)
        } else {
            // Show timer button
            EnhancedControlButton(
                icon = Icons.Default.Timer,
                size = 56.dp,
                backgroundColor = Color.White.copy(alpha = 0.2f),
                iconColor = Color.White,
                enabled = !isRecording,
                onClick = { showTimerOptions = true }
            )
        }
    }
}

@Composable
private fun TimerCountdownDisplay(countdown: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "countdown")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .size(56.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = Color.Red.copy(alpha = 0.9f)
        ),
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = countdown.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun TimerSelectionDialog(
    onTimerSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        0 to "No Timer",
        1 to "1 Second",
        3 to "3 Seconds",
        5 to "5 Seconds",
        10 to "10 Seconds"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Timer",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Recording Timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose when to start recording:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                timerOptions.forEach { (seconds, label) ->
                    TimerOption(
                        seconds = seconds,
                        label = label,
                        onClick = { onTimerSelected(seconds) }
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
private fun TimerOption(
    seconds: Int,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (seconds == 0) Icons.Default.PlayArrow else Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            if (seconds > 0) {
                Text(
                    text = "${seconds}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MergeControlButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "mergeButtonScale"
    )

    // Pulsing animation to draw attention
    val infiniteTransition = rememberInfiniteTransition(label = "mergePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.3f),
                CircleShape
            )
            .border(
                2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                CircleShape
            )
            .clickable(enabled = enabled) {
                if (enabled) {
                    isPressed = true
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MergeType,
                contentDescription = "Merge Audio & Video",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Merge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Updated EnhancedTopStatusBar to show role information
@Composable
private fun EnhancedTopStatusBar(
    isRecording: Boolean,
    cameraStatus: String,
    isConnected: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    cameraManager: CameraManager,
    recordingTime: String,
    hasReceivedAudio: Boolean = false,
    appState: AppState
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Enhanced recording status with role info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated recording indicator
                    AnimatedRecordingDot(isRecording = isRecording)

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isRecording) "RECORDING" else "READY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Role badge
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "📹 Camera",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = cameraStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        // Show audio received indicator
                        if (hasReceivedAudio) {
                            Text(
                                text = "🎵 Audio received from ${appState.remoteRole}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Green
                            )
                        }
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

                        if (isConnected && appState.remoteRole.isNotEmpty()) {
                            Text(
                                text = "Connected to ${appState.remoteRole}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Green
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSwitchConfirmationDialog(
    currentRole: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val targetRole = if (currentRole == "camera") "microphone" else "camera"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch Roles",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Switch Roles?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You are currently the $currentRole device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Switching will make you the $targetRole and the remote device will become the $currentRole.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚠️ Any current recordings will be stopped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch to $targetRole")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MergeAvailableButton(
    onMergeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onMergeClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MergeType,
                contentDescription = "Merge",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Merge Audio & Video",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
    appState: AppState,

    connectionManager: ConnectionManager,
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