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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState

@Composable
fun ConnectingScreen(
    appState: AppState,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
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
            // Animated Connection Visual
            AnimatedConnectionVisual()

            // Status Information
            StatusInformationCard(appState = appState)

            // Connection steps
            ConnectionStepsCard()

            // Cancel button
            CancelConnectionButton(onBack = onBack)
        }
    }
}

@Composable
private fun AnimatedConnectionVisual() {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer rotating ring
        val infiniteTransition = rememberInfiniteTransition(label = "connecting")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        // Outer pulsing circles
        repeat(3) { index ->
            val scale = pulseScale - (index * 0.1f)
            val alpha = (1f - index * 0.3f) * 0.7f

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        CircleShape
                    )
            )
        }

        // Rotating border
        Box(
            modifier = Modifier
                .size(160.dp)
                .rotate(rotation)
                .border(
                    4.dp,
                    Brush.sweepGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary
                        )
                    ),
                    CircleShape
                )
        )

        // Center connection icon
        Card(
            modifier = Modifier.size(100.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun StatusInformationCard(appState: AppState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Connecting...",
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

            // Progress indicator with dots - separate infinite transition
            AnimatedProgressDots()
        }
    }
}

@Composable
private fun AnimatedProgressDots() {
    val dotsInfiniteTransition = rememberInfiniteTransition(label = "progress_dots")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val animationDelay = index * 200
            val dotScale by dotsInfiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = animationDelay,
                        easing = EaseInOutBounce
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(dotScale)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ConnectionStepsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connection Process:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            ConnectionStep(
                stepNumber = 1,
                title = "Discovering Device",
                isActive = true,
                isCompleted = true
            )

            ConnectionStep(
                stepNumber = 2,
                title = "Establishing Connection",
                isActive = true,
                isCompleted = false
            )

            ConnectionStep(
                stepNumber = 3,
                title = "Verifying Security",
                isActive = false,
                isCompleted = false
            )
        }
    }
}

@Composable
private fun CancelConnectionButton(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
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
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Cancel Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ConnectionStep(
    stepNumber: Int,
    title: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Step indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Step title
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isActive || isCompleted) FontWeight.Medium else FontWeight.Normal
        )

        Spacer(modifier = Modifier.weight(1f))

        // Status indicator
        if (isActive && !isCompleted) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.secondary,
                        CircleShape
                    )
            )
        }
    }
}