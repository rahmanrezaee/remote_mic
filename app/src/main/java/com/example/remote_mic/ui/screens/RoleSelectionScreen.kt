package com.example.remote_mic.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.component.CleanStatusHeader
import com.example.remote_mic.ui.theme.RemoteMicTheme

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay

@Composable
fun RoleSwitchFloatingButton(
    appState: AppState,
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier
) {
    var isAnimating by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "roleSwitch")
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

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(enabled = !appState.isRecording) {
                if (!appState.isRecording) {
                    isAnimating = true
                    connectionManager.switchRoles()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = CircleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch Roles",
                modifier = Modifier
                    .size(28.dp)
                    .rotate(if (isAnimating) rotation else 0f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            delay(300)
            isAnimating = false
        }
    }
}

@Composable
fun RoleSwitchCard(
    appState: AppState,
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "switchCardScale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(enabled = !appState.isRecording) {
                if (!appState.isRecording) {
                    isPressed = true
                    connectionManager.switchRoles()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (appState.isRecording) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current role icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (appState.myRole) {
                        "camera" -> Icons.Default.Videocam
                        "mic" -> Icons.Default.Mic
                        else -> Icons.Default.Person
                    },
                    contentDescription = "Current Role",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Switch icon with animation
            RoleSwitchIcon(isEnabled = !appState.isRecording)

            // Target role icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (appState.myRole) {
                        "camera" -> Icons.Default.Mic
                        "mic" -> Icons.Default.Videocam
                        else -> Icons.Default.Person
                    },
                    contentDescription = "Target Role",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Switch Roles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (appState.isRecording) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = if (appState.isRecording) {
                        "Stop recording to switch"
                    } else {
                        "Tap to become ${if (appState.myRole == "camera") "microphone" else "camera"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(150)
            isPressed = false
        }
    }
}

@Composable
private fun RoleSwitchIcon(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "switchIcon")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "iconRotation"
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Switch",
            modifier = Modifier
                .size(16.dp)
                .rotate(if (isEnabled) rotation else 0f),
            tint = if (isEnabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun RoleStatusIndicator(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local device role
            RoleChip(
                role = appState.myRole,
                label = "You",
                isLocal = true
            )

            // Connection indicator
            ConnectionLine(isConnected = appState.isConnected)

            // Remote device role
            RoleChip(
                role = appState.remoteRole,
                label = "Remote",
                isLocal = false
            )
        }
    }
}

@Composable
private fun RoleChip(
    role: String,
    label: String,
    isLocal: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (role) {
                    "camera" -> MaterialTheme.colorScheme.primaryContainer
                    "mic" -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (role) {
                        "camera" -> Icons.Default.Videocam
                        "mic" -> Icons.Default.Mic
                        else -> Icons.Default.Person
                    },
                    contentDescription = "$label Role",
                    modifier = Modifier.size(24.dp),
                    tint = when (role) {
                        "camera" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "mic" -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = role.takeIf { it.isNotEmpty() }?.capitalize() ?: "Waiting",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (role.isNotEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
    }
}

@Composable
private fun ConnectionLine(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connection")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineAlpha"
    )

    Box(
        modifier = modifier
            .width(40.dp)
            .height(2.dp)
            .background(
                if (isConnected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                },
                RoundedCornerShape(1.dp)
            )
    )
}

// Extension function to capitalize first letter
private fun String.capitalize(): String {
    return if (isEmpty()) this else this[0].uppercaseChar() + substring(1)
}
@Composable
fun RoleSelectionScreen(onSelectRole: (String) -> Unit, appState: AppState,    connectionManager: ConnectionManager) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp), // Make it scrollable
    ) {
        // Animated Header

        CleanStatusHeader(appState = appState, connectionManager = connectionManager)

        // Role Selection Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Camera Role Card
            RoleCard(
                title = "As Camera Device",
                description = "Record video and control the recording session",
                icon = Icons.Default.Videocam,

                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                iconBackgroundColor = MaterialTheme.colorScheme.primary,
                iconContentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { onSelectRole("camera") }
            )

            // Microphone Role Card
            RoleCard(
                title = "As Microphone Device",
                description = "Capture high-quality audio for the recording",
                icon = Icons.Default.Mic,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBackgroundColor = MaterialTheme.colorScheme.secondary,
                iconContentColor = MaterialTheme.colorScheme.onSecondary,
                onClick = { onSelectRole("mic") }
            )
        }
    }

}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconBackgroundColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Card(
        onClick = {
            isHovered = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = if (isHovered) 12.dp else 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = containerColor,
                spotColor = containerColor
            )
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor.copy(alpha = 0.95f))
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(0.dp) // Shadow already applied manually
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(iconBackgroundColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Navigate",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.8f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            kotlinx.coroutines.delay(150)
            isHovered = false
        }
    }
}
