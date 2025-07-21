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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState
import com.example.remote_mic.DeviceRole

@Composable
fun RoleSelectionScreen(
    appState: AppState,
    onSelectRole: (DeviceRole) -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // Success animation and title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Animated success icon
                val infiniteTransition = rememberInfiniteTransition(label = "success")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "successScale"
                )

                val rotation by infiniteTransition.animateFloat(
                    initialValue = -5f,
                    targetValue = 5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rotation"
                )

                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing background circles
                    repeat(3) { index ->
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 2000,
                                    delayMillis = index * 300,
                                    easing = EaseInOutSine
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse$index"
                        )

                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .scale(pulseScale)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f - index * 0.03f),
                                    CircleShape
                                )
                        )
                    }

                    // Main success icon
                    Card(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(scale),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = CircleShape,
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Success message
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎉 Connected Successfully!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = appState.statusMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Connection details
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Connected to ${appState.connectedDevice?.name ?: "Remote Device"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Role selection prompt
            Text(
                text = "Choose your role to continue:",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            // Role Selection Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Camera Role Card
                RoleCard(
                    title = "Camera",
                    description = "Record video and control the recording session",
                    icon = Icons.Default.Camera,
                    features = listOf(
                        "Control video recording",
                        "Manage recording settings",
                        "Receive audio from microphone",
                        "Merge final video files"
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconBackgroundColor = MaterialTheme.colorScheme.primary,
                    iconContentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { onSelectRole(DeviceRole.CAMERA) }
                )

                // Microphone Role Card
                RoleCard(
                    title = "Microphone",
                    description = "Capture high-quality audio for the recording",
                    icon = Icons.Default.Mic,
                    features = listOf(
                        "Record high-quality audio",
                        "Real-time audio monitoring",
                        "Sync with video recording",
                        "Send audio to camera device"
                    ),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconBackgroundColor = MaterialTheme.colorScheme.secondary,
                    iconContentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = { onSelectRole(DeviceRole.MICROPHONE) }
                )
            }

            // Disconnect option
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Need to change connection?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    features: List<String>,
    containerColor: Color,
    contentColor: Color,
    iconBackgroundColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )

    Card(
        onClick = {
            isHovered = !isHovered
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered) 12.dp else 6.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            iconBackgroundColor,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = iconContentColor
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Select",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Features list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )

                features.forEach { feature ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = iconBackgroundColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}