package com.example.remote_mic.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.remote_mic.managers.CameraManager

@Composable
fun CameraScreen(
    isRecording: Boolean,
    cameraManager: CameraManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Status Card
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎥 Camera Mode", style = MaterialTheme.typography.titleLarge)
                Text("Status: ${if (isRecording) "🔴 RECORDING" else "⚪ READY"}")
                Text("Controls: Start/stop recording syncs with microphone device")
            }
        }

        // Camera Preview
        Card(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraManager.initializeCamera(this, lifecycleOwner)
                    }
                }
            )
        }

        // Recording Controls
        if (isRecording) {
            Button(
                onClick = onStopRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("⏹ Stop Recording", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("⏺ Start Recording", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}