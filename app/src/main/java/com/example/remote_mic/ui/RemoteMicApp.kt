package com.example.remote_mic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.component.TransferProgressIndicator
import com.example.remote_mic.ui.screens.*

@Composable
fun RemoteMicApp(
    connectionManager: ConnectionManager,
    audioManager: AudioManager,
    cameraManager: CameraManager
) {
    val appState by connectionManager.state.collectAsStateWithLifecycle()

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
            modifier = Modifier
                .fillMaxSize(),

            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Main Content Area
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
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

        // Transfer progress indicator
        if (appState.isSendingFile || appState.transferProgress.isNotEmpty()) {
            TransferProgressIndicator(
                progress = appState.transferProgress,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}