package com.example.remote_mic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.ConnectionManager
import com.example.remote_mic.ui.components.AudioFileCard
import com.example.remote_mic.utils.openMediaFile

@Composable
fun MicrophoneScreen(
    appState: AppState,
    audioManager: AudioManager,
    connectionManager: ConnectionManager
) {
    val context = LocalContext.current

    // Auto-sync recording with remote camera
    LaunchedEffect(appState.isRecording) {
        if (appState.isRecording) {
            audioManager.startRecording()
        } else {
            audioManager.stopRecording()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Status Card
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎤 Microphone Mode", style = MaterialTheme.typography.titleLarge)
                Text("Status: ${if (appState.isRecording) "🔴 RECORDING" else "⚪ READY"}")
                Text(
                    "Automatically synced with camera device",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Recording Indicator
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (appState.isRecording) "🎤 RECORDING" else "🎤 STANDBY",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (appState.isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (appState.isRecording)
                        "Recording audio..."
                    else
                        "Waiting for camera to start...",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (appState.isSendingFile) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sending audio file...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Audio File Preview and Send
        appState.pendingAudioFile?.let { audioFile ->
            AudioFileCard(
                audioFile = audioFile,
                onPreview = { openMediaFile(context, audioFile) },
                onSend = { connectionManager.sendPendingAudioFile() },
                isSending = appState.isSendingFile
            )
        }

        // Audio Status
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Audio Status", style = MaterialTheme.typography.titleMedium)
                Text(audioManager.getStatus())
            }
        }
    }
}