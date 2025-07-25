package com.example.remote_mic.ui.components

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.remote_mic.data.AppState
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import kotlinx.coroutines.delay

@Composable
private fun BlinkingRecordingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "blinking_dot")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                Color.Red.copy(alpha = alpha),
                CircleShape
            )
    )
}

@Composable
fun UnifiedEnhancedStatusBar(
    appState: AppState,
    connectionManager: ConnectionManager,
    isRecording: Boolean,
    recordingTime: String = "00:00",
    // Camera-specific parameters (nullable for mic screen)
    cameraManager: CameraManager? = null,
    cameraStatus: String? = null,
    onSettingsClick: (() -> Unit)? = null,
    hasReceivedAudio: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showRoleSwitchDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (appState.myRole == "camera") {
                Color.Black.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (appState.myRole == "camera") 0.dp else 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Row - Connection Status and Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side - Connection Status
                ConnectionStatusSection(
                    isConnected = appState.isConnected,
                    remoteRole = appState.remoteRole,
                    textColor = if (appState.myRole == "camera") Color.White else MaterialTheme.colorScheme.onSurface,
                    isRecording = isRecording,
                    recordingTime = recordingTime
                )

                // Right Side - Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Switch Role Button
                    ActionButton(
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = "Switch Roles",
                        enabled = !isRecording,
                        onClick = { showRoleSwitchDialog = true },
                        isCamera = appState.myRole == "camera"
                    )

                    // Disconnect Button
                    ActionButton(
                        icon = Icons.Outlined.PowerSettingsNew,
                        contentDescription = "Disconnect",
                        enabled = true,
                        onClick = { connectionManager.disconnect() },
                        isCamera = appState.myRole == "camera",
                        isDestructive = true
                    )
                }
            }

        }
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
}

@Composable
private fun ConnectionStatusSection(
    isConnected: Boolean,
    remoteRole: String,
    textColor: Color,
    isRecording: Boolean = false,
    recordingTime: String = "00:00"
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Connection Status
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        // Show timer when recording, otherwise show remote device info
        if (isRecording) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Blinking red dot
                BlinkingRecordingDot()

                Text(
                    text = recordingTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Remote device info (only when not recording)
            if (isConnected && remoteRole.isNotEmpty()) {
                Text(
                    text = "to $remoteRole device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "No device connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isCamera: Boolean,
    isDestructive: Boolean = false
) {
    var isAnimating by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "buttonAnimation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 0.9f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    FilledTonalIconButton(
        onClick = {
            isAnimating = true
            onClick()
        },
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = when {
                isDestructive -> if (isCamera) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                isCamera -> Color.White.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = when {
                isDestructive -> if (isCamera) Color.Red else MaterialTheme.colorScheme.onErrorContainer
                isCamera -> Color.White
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            disabledContainerColor = if (isCamera) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = if (isCamera) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (isAnimating && enabled && icon == Icons.Default.SwapHoriz) rotation else 0f)
        )
    }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            delay(300)
            isAnimating = false
        }
    }
}

@Composable
private fun TimerSection(
    recordingTime: String,
    isRecording: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = recordingTime,
            style = MaterialTheme.typography.headlineMedium,
            color = if (isRecording) Color.Red else textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoleSwitchConfirmationDialog(
    currentRole: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val targetRole = if (currentRole == "camera") "mic" else "camera"

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