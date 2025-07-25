package com.example.remote_mic.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.screens.AudioEditorScreen
import com.example.remote_mic.ui.screens.CameraScreen
import com.example.remote_mic.ui.screens.ConnectionScreen
import com.example.remote_mic.ui.screens.MicrophoneScreen
import com.example.remote_mic.ui.screens.RoleSelectionScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RemoteMicApp(
    connectionManager: ConnectionManager,
    audioManager: AudioManager,
    cameraManager: CameraManager
) {
    val appState by connectionManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var showExitConfirmation by remember { mutableStateOf(false) }
    var backPressedOnce by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Check if we can merge (have both files and are on camera side)
    val canMerge = appState.myRole == "camera" &&
            appState.receivedAudioFile != null &&
            appState.recordedVideoFile != null

    // Update the connection manager when merge capability changes
    LaunchedEffect(canMerge) {
        connectionManager.updateState { copy(canMerge = canMerge) }
    }

    // Double back press to exit with toast
    BackHandler {
        val currentTime = System.currentTimeMillis()

        if (backPressedOnce && (currentTime - lastBackPressTime) < 2000) {
            // Second press within 2 seconds - exit app
            if (appState.isRecording) {
                when (appState.myRole) {
                    "camera" -> cameraManager.stopRecording()
                    "mic" -> audioManager.stopRecording()
                }
            }
            connectionManager.cleanup()
            activity?.finish()
        } else {
            // First press - show toast and set flag
            backPressedOnce = true
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()

            // Reset flag after 2 seconds
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Main Content Area
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Show merge screen when requested and files are available
                    appState.showMergeScreen && canMerge -> {
                        AudioEditorScreen(
                            videoFile = appState.recordedVideoFile!!,
                            audioFile = appState.receivedAudioFile!!,
                            onBackToCamera = {
                                connectionManager.updateState { copy(showMergeScreen = false) }
                            },
                            onExportComplete = { mergedFile ->
                                connectionManager.updateState {
                                    copy(
                                        mergedVideoFile = mergedFile,
                                        showMergeScreen = false
                                    )
                                }
                            }
                        )
                    }

                    !appState.isConnected -> {
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

                    appState.myRole.isEmpty() -> {
                        RoleSelectionScreen(
                            appState = appState,
                            onSelectRole = { role -> connectionManager.selectRole(role) },
                            connectionManager = connectionManager
                        )
                    }

                    appState.myRole == "camera" -> {
                        CameraScreen(
                            appState = appState,
                            connectionManager = connectionManager,
                            isRecording = appState.isRecording,
                            cameraManager = cameraManager,
                            onStartRecording = { cameraManager.startRecording() },
                            onStopRecording = { cameraManager.stopRecording() }
                        )
                    }

                    appState.myRole == "mic" -> {
                        MicrophoneScreen(
                            appState = appState,
                            audioManager = audioManager,
                            connectionManager = connectionManager
                        )
                    }
                }
            }
        }

        // Remove the exit confirmation dialog since we're using toast now
        // No dialog needed anymore
    }
}

// SimpleExitDialog removed since we're using the toast approach now