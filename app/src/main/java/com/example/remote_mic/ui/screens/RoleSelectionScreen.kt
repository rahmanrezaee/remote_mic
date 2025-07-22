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
