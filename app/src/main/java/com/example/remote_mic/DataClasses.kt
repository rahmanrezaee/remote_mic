package com.example.remote_mic

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Command(val action: String, val data: String = "")

data class AppState(
    val isConnected: Boolean = false,
    val myRole: String = "", // "camera" or "mic"
    val isRecording: Boolean = false,
    val statusMessage: String = "Ready to connect",
    val discoveredDevices: List<String> = emptyList(),
    val receivedAudioFile: File? = null,
    val pendingAudioFile: File? = null,
    val recordedVideoFile: File? = null,
    val isSendingFile: Boolean = false,
    val transferProgress: String = "",
    val lastError: String? = null
)