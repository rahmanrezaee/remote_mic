package com.example.remote_mic.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.AppState
import com.example.remote_mic.AudioManager

@Composable
fun MicrophoneScreen(
    appState: AppState,
    audioManager: AudioManager,
    onDisconnect: () -> Unit
) {
    val audioState by audioManager.state.collectAsStateWithLifecycle()

    // Handle recording commands from camera device
    LaunchedEffect(appState.recordingState) {
        audioManager.handleRemoteCommand(
            when (appState.recordingState) {
                "recording" -> "start_recording"
                "paused" -> "pause_recording"
                "idle" -> "stop_recording"
                else -> return@LaunchedEffect
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Top Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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
                            text = "Microphone Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    audioState.isRecording && !audioState.isPaused -> Icons.Default.FiberManualRecord
                                    audioState.isPaused -> Icons.Default.Pause
                                    else -> Icons.Default.Circle
                                },
                                contentDescription = null,
                                tint = when {
                                    audioState.isRecording && !audioState.isPaused -> Color.Red
                                    audioState.isPaused -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when {
                                    audioState.isRecording && !audioState.isPaused -> "Recording Audio"
                                    audioState.isPaused -> "Paused"
                                    audioState.isProcessingFile -> "Processing..."
                                    else -> "Standby"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (audioState.recordingDuration > 0) {
                                Text(
                                    text = formatDuration(audioState.recordingDuration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect")
                    }
                }
            }

            // Audio Visualization
            Box(
                modifier = Modifier.size(250.dp),
                contentAlignment = Alignment.Center
            ) {
                // Audio level visualization circles
                repeat(4) { index ->
                    val baseSize = 150f
                    val sizeMultiplier = 1f + (index * 0.3f)
                    val alpha = if (audioState.isRecording && !audioState.isPaused) {
                        (audioState.audioLevel * (1f - index * 0.2f)).coerceIn(0f, 1f)
                    } else {
                        0.1f + (index * 0.05f)
                    }

                    Box(
                        modifier = Modifier
                            .size((baseSize * sizeMultiplier).dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                CircleShape
                            )
                    )
                }

                // Center microphone icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            if (audioState.isRecording && !audioState.isPaused) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Status Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = when {
                            audioState.isRecording && !audioState.isPaused -> "Recording High-Quality Audio"
                            audioState.isPaused -> "Recording Paused"
                            audioState.isProcessingFile -> "Processing Audio File..."
                            else -> "Ready to Record"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Controlled by ${appState.connectedDevice?.name ?: "camera device"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Audio Level Meter
                    if (audioState.isRecording && !audioState.isPaused) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Audio Level",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LinearProgressIndicator(
                                progress = audioState.audioLevel,
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    audioState.audioLevel > 0.8f -> MaterialTheme.colorScheme.error
                                    audioState.audioLevel > 0.6f -> Color(0xFFFF9800) // Orange
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "${(audioState.audioLevel * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Processing Indicator
                    if (audioState.isProcessingFile) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "processing")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rotation"
                            )

                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotation),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Sending audio to camera device...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Recording Info
            audioState.outputFile?.let { outputFile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Recording to:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = outputFile.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (outputFile.exists()) {
                            Text(
                                text = "Size: ${formatFileSize(outputFile.length())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Error Message
        audioState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60)) % 24

    return buildString {
        if (hours > 0) append("$hours:")
        append(String.format("%02d:%02d", minutes, seconds))
    }
}