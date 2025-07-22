package com.example.remote_mic.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TransferProgressIndicator(
    progress: String,
    modifier: Modifier = Modifier
) {
    val isSuccess = progress.startsWith("✅")
    val isError = progress.startsWith("❌")
    val isActive = progress.isNotEmpty() && !isSuccess && !isError

    val containerColor = when {
        isSuccess -> MaterialTheme.colorScheme.primaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        isActive -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val contentColor = when {
        isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isActive -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when {
        isSuccess -> Icons.Default.CheckCircle
        isError -> Icons.Default.Error
        isActive -> Icons.Default.CloudUpload
        else -> Icons.Default.Info
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Icon
            AnimatedIcon(
                icon = icon,
                isActive = isActive,
                tint = contentColor
            )

            // Progress Text
            Text(
                text = progress,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )

            // Progress Animation for Active Transfers
            if (isActive && progress.contains("%")) {
                val percentageText = progress.substringAfter(": ").substringBefore("%")
                val percentage = percentageText.toFloatOrNull()?.div(100f) ?: 0f

                CircularProgressIndicator(
                    progress = percentage,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.3f)
                )
            } else if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    tint: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon_animation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActive) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    rotationZ = if (icon == Icons.Default.CloudUpload) rotation else 0f
                    scaleX = scale
                    scaleY = scale
                },
            tint = tint
        )
    }
}