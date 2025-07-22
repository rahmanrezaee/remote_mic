package com.example.remote_mic.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.AppState
import com.example.remote_mic.Command
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.component.TransferProgressIndicator
import com.example.remote_mic.ui.screens.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RemoteMicApp(
    connectionManager: ConnectionManager,
    audioManager: AudioManager,
    cameraManager: CameraManager
) {
    val appState by connectionManager.state.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(AppScreen.CONNECTION) }

    // Auto-navigate based on state
    LaunchedEffect(appState.isConnected, appState.myRole) {
        currentScreen = when {
            !appState.isConnected -> AppScreen.CONNECTION
            appState.myRole.isEmpty() -> AppScreen.ROLE_SELECTION
            appState.myRole == "camera" -> AppScreen.CAMERA
            appState.myRole == "mic" -> AppScreen.MICROPHONE
            else -> AppScreen.CONNECTION
        }
    }

    // Check if we have both audio and video files for merging
    val showMergeOption = appState.receivedAudioFile != null && appState.recordedVideoFile != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.background
                    ),
                    radius = 800f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Enhanced Top Bar
            EnhancedTopBar(
                currentScreen = currentScreen,
                appState = appState,
                showMergeOption = showMergeOption,
                onNavigateToMerge = { currentScreen = AppScreen.MERGE },
                onDisconnect = { connectionManager.disconnect() }
            )

            // Main Content with Smooth Transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { if (targetState.ordinal > initialState.ordinal) 300 else -300 },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(400)) with
                                slideOutHorizontally(
                                    targetOffsetX = { if (targetState.ordinal > initialState.ordinal) -300 else 300 },
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(400))
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        AppScreen.CONNECTION -> {
                            ConnectionScreen(
                                appState = appState,
                                connectionManager = connectionManager,
                                onHost = { connectionManager.startHosting() },
                                onSearch = { connectionManager.startSearching() },
                                onConnect = { device ->
                                    val endpointId = device.split("|")[1]
                                    connectionManager.connectToDevice(endpointId)
                                }
                            )
                        }

                        AppScreen.ROLE_SELECTION -> {
                            RoleSelectionScreen(
                                appState = appState,
                                onSelectRole = { role -> connectionManager.selectRole(role) },
                                connectionManager = connectionManager
                            )
                        }

                        AppScreen.CAMERA -> {
                            CameraScreen(
                                isRecording = appState.isRecording,
                                cameraManager = cameraManager,
                                onStartRecording = {
                                    cameraManager.startRecording()
                                    connectionManager.sendCommand(Command("record", "start"))
                                },
                                onStopRecording = {
                                    cameraManager.stopRecording()
                                    connectionManager.sendCommand(Command("record", "stop"))
                                },
                                appState = appState,
                                connectionManager = connectionManager
                            )
                        }

                        AppScreen.MICROPHONE -> {
                            MicrophoneScreen(
                                appState = appState,
                                audioManager = audioManager,
                                connectionManager = connectionManager
                            )
                        }

                        AppScreen.MERGE -> {
                            MediaMergeScreen(
                                appState = appState,
                                connectionManager = connectionManager,
                                onBack = { currentScreen = if (appState.myRole == "camera") AppScreen.CAMERA else AppScreen.MICROPHONE }
                            )
                        }
                    }
                }
            }
        }

        // Enhanced Transfer Progress Indicator
        AnimatedVisibility(
            visible = appState.isSendingFile || appState.transferProgress.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TransferProgressIndicator(
                progress = appState.transferProgress,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Error Snackbar
        appState.lastError?.let { error ->
            LaunchedEffect(error) {
                // Auto-clear error after 5 seconds
                kotlinx.coroutines.delay(5000)
                connectionManager.updateState { copy(lastError = null) }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { connectionManager.updateState { copy(lastError = null) } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedTopBar(
    currentScreen: AppScreen,
    appState: AppState,
    showMergeOption: Boolean,
    onNavigateToMerge: () -> Unit,
    onDisconnect: () -> Unit
) {
    if (currentScreen == AppScreen.CONNECTION) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Status
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (appState.isConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (appState.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (appState.isConnected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (appState.isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (appState.isConnected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Role Badge
                if (appState.myRole.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (appState.myRole == "camera") Icons.Default.Videocam else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = appState.myRole.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Recording Status
                if (appState.isRecording) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = "REC",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Red
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Merge Button
                if (showMergeOption) {
                    FilledTonalButton(
                        onClick = onNavigateToMerge,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Merge")
                    }
                }

                // Disconnect Button
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Disconnect")
                }
            }
        }
    }
}

enum class AppScreen {
    CONNECTION,
    ROLE_SELECTION,
    CAMERA,
    MICROPHONE,
    MERGE
}