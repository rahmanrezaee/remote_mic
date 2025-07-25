package com.example.remote_mic.data

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Command(val action: String, val data: String = "")

data class AppState(
    val isConnected: Boolean = false,
    val myRole: String = "", // "camera" or "mic"
    val remoteRole: String = "", // Track remote device role
    val isRecording: Boolean = false,
    val statusMessage: String = "Ready to connect",
    val discoveredDevices: List<String> = emptyList(),
    val receivedAudioFile: File? = null,
    val pendingAudioFile: File? = null,
    val recordedVideoFile: File? = null,
    val isSendingFile: Boolean = false,
    val transferProgress: String = "",
    val lastError: String? = null,
    // Merge functionality fields
    val showMergeScreen: Boolean = false,
    val mergedVideoFile: File? = null,
    val canMerge: Boolean = false // true when we have both video and audio files
)