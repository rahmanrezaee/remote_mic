package com.example.remote_mic.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.components.FileStatusCard
import com.example.remote_mic.ui.screens.*

@Composable
fun RemoteMicApp(
    connectionManager: ConnectionManager,
    audioManager: AudioManager,
    cameraManager: CameraManager
) {
    val appState by connectionManager.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card with Error Display
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = appState.statusMessage,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (appState.transferProgress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = appState.transferProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                appState.lastError?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        when {
            !appState.isConnected -> {
                ConnectionScreen(
                    appState = appState,
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
                    onSelectRole = { role -> connectionManager.selectRole(role) }
                )
            }

            appState.myRole == "camera" -> {
                CameraScreen(
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

        // File Status with clickable files
        if (appState.recordedVideoFile != null || appState.receivedAudioFile != null) {
            FileStatusCard(
                videoFile = appState.recordedVideoFile,
                audioFile = appState.receivedAudioFile
            )
        }

        // Disconnect Button and Debug Info
        if (appState.isConnected) {
            Button(
                onClick = { connectionManager.disconnect() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("🔌 Disconnect")
            }

            OutlinedButton(
                onClick = {
                    Log.d("DEBUG", connectionManager.getConnectionDiagnostics())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🐛 Debug Info")
            }
        }
    }
}