package com.example.remote_mic.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState

@Composable
fun HostingScreen(
    appState: AppState,
    onDismissInstructions: () -> Unit,
    onDisconnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
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
            // Animated Connection Circle
            AnimatedHostingCircle()

            // Status Card
            StatusCard(
                appState = appState
            )

            // Connection Info Card
            ConnectionInfoCard()

            // Cancel button
            CancelButton(onDisconnect = onDisconnect)
        }

        // Instructions Dialog
        if (appState.showInstructions) {
            InstructionsDialog(onDismiss = onDismissInstructions)
        }
    }
}

@Composable
private fun AnimatedHostingCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "hosting_circle")

    Box(
        modifier = Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Animated pulsing rings
        val pulse1 by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse1"
        )
        val pulse2 by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = EaseInOutSine, delayMillis = 400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse2"
        )
        val pulse3 by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = EaseInOutSine, delayMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse3"
        )

        // Outer rings
        repeat(3) { index ->
            val scale = when (index) {
                0 -> pulse1
                1 -> pulse2
                else -> pulse3
            }
            val alpha = 0.3f - (index * 0.08f)

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        CircleShape
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.6f),
                        CircleShape
                    )
            )
        }

        // Main hosting circle
        Card(
            modifier = Modifier.size(140.dp),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "HOSTING",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(appState: AppState) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_dots")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Waiting for Connection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = appState.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Connection indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val dotScale by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                delayMillis = index * 200,
                                easing = EaseInOutBounce
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "statusDot$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(dotScale)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Connection Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow(
                    label = "Device Name:",
                    value = android.os.Build.MODEL
                )
                InfoRow(
                    label = "Status:",
                    value = "Broadcasting"
                )
                InfoRow(
                    label = "Waiting for:",
                    value = "Remote device to join"
                )
            }
        }
    }
}

@Composable
private fun CancelButton(onDisconnect: () -> Unit) {
    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            )
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Cancel Hosting",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun InstructionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("How to Connect")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "To connect another device:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstructionStep(
                        stepNumber = 1,
                        instruction = "Open this app on the other device"
                    )
                    InstructionStep(
                        stepNumber = 2,
                        instruction = "Tap 'Join Connection'"
                    )
                    InstructionStep(
                        stepNumber = 3,
                        instruction = "Select this device from the list"
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Make sure both devices have Bluetooth and WiFi enabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun InstructionStep(
    stepNumber: Int,
    instruction: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}