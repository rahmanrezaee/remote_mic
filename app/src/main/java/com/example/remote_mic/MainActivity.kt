// ============================================================================
// COMPLETE FIXED REMOTE MIC APP - All Issues Resolved
// ============================================================================

package com.example.remote_mic

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.nio.charset.StandardCharsets

// ============================================================================
// DATA CLASSES
// ============================================================================

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

// ============================================================================
// MAIN ACTIVITY
// ============================================================================

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private lateinit var connectionManager: ConnectionManager
    private lateinit var audioManager: AudioManager
    private lateinit var cameraManager: CameraManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeApp()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.e(TAG, "Denied permissions: $deniedPermissions")
            Toast.makeText(
                this,
                "Required permissions denied: ${deniedPermissions.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate - Starting app initialization")

        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }

        val hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "Permissions check - All granted: $hasAllPermissions")

        if (hasAllPermissions) {
            initializeApp()
        } else {
            Log.d(TAG, "Requesting permissions: $requiredPermissions")
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    private fun initializeApp() {
        Log.d(TAG, "Initializing app components...")

        try {
            connectionManager = ConnectionManager(this)
            audioManager = AudioManager(this)
            cameraManager = CameraManager(this)

            // Connect callbacks
            cameraManager.onRecordingStateChanged = { isRecording ->
                Log.d(TAG, "Camera recording state changed: $isRecording")
                connectionManager.updateRecordingState(isRecording)
                if (isRecording) {
                    connectionManager.sendCommand(Command("record", "start"))
                } else {
                    connectionManager.sendCommand(Command("record", "stop"))
                }
            }

            cameraManager.onVideoFileReady = { videoFile ->
                Log.d(TAG, "Video file ready: ${videoFile.name}")
                connectionManager.setVideoFile(videoFile)
            }

            audioManager.onAudioFileReady = { audioFile ->
                Log.d(TAG, "Audio file ready: ${audioFile.name}, size: ${audioFile.length()}")
                connectionManager.updateState { copy(pendingAudioFile = audioFile) }
            }

            audioManager.onRecordingError = { error ->
                Log.e(TAG, "Audio recording error: $error")
                connectionManager.updateState { copy(lastError = "Audio error: $error") }
            }

            setContent {
                MaterialTheme {
                    RemoteMicApp(
                        connectionManager = connectionManager,
                        audioManager = audioManager,
                        cameraManager = cameraManager
                    )
                }
            }

            Log.d(TAG, "App initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize app", e)
            Toast.makeText(this, "App initialization failed: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy - Cleaning up...")
        super.onDestroy()
        if (::connectionManager.isInitialized) {
            connectionManager.cleanup()
        }
        if (::audioManager.isInitialized) {
            audioManager.cleanup()
        }
    }
}

// ============================================================================
// IMPROVED CONNECTION MANAGER
// ============================================================================

class ConnectionManager(private val context: Context) {
    private val TAG = "ConnectionManager"
    private val SERVICE_ID = "remote_mic_app_v2"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null

    // Track pending file transfers with metadata
    private data class FileTransfer(
        val filename: String,
        val expectedSize: Long,
        val startTime: Long = System.currentTimeMillis()
    )

    private val pendingFiles = mutableMapOf<Long, FileTransfer>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun setVideoFile(videoFile: File) {
        Log.d(TAG, "Setting video file: ${videoFile.name}")
        updateState { copy(recordedVideoFile = videoFile) }
    }

    fun sendPendingAudioFile() {
        val pendingFile = _state.value.pendingAudioFile
        if (pendingFile == null) {
            Log.w(TAG, "No pending audio file to send")
            updateState { copy(lastError = "No audio file to send") }
            return
        }

        Log.d(TAG, "Sending pending audio file: ${pendingFile.name}")
        sendAudioFile(pendingFile)
        updateState { copy(pendingAudioFile = null) }
    }

    fun startHosting() {
        Log.d(TAG, "Starting to host connection...")
        updateState { copy(statusMessage = "Starting host...", lastError = null) }

        val deviceName = "Host_${Build.MODEL}_${System.currentTimeMillis() % 10000}"

        connectionsClient.startAdvertising(
            deviceName,
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_STAR)
                .build()
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started successfully")
            updateState { copy(statusMessage = "Hosting as: $deviceName\nWaiting for connection...") }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
            updateState { copy(statusMessage = "Failed to host", lastError = e.message) }
        }
    }

    fun startSearching() {
        Log.d(TAG, "Starting device discovery...")
        updateState {
            copy(
                statusMessage = "Searching for devices...",
                discoveredDevices = emptyList(),
                lastError = null
            )
        }

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_STAR)
                .build()
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
            updateState { copy(statusMessage = "Failed to search", lastError = e.message) }
        }
    }

    fun connectToDevice(endpointId: String) {
        Log.d(TAG, "Connecting to device: $endpointId")
        updateState { copy(statusMessage = "Connecting...", lastError = null) }

        val deviceName = "Client_${Build.MODEL}_${System.currentTimeMillis() % 10000}"

        connectionsClient.requestConnection(
            deviceName,
            endpointId,
            connectionCallback
        ).addOnFailureListener { e ->
            Log.e(TAG, "Failed to request connection", e)
            updateState { copy(statusMessage = "Connection request failed", lastError = e.message) }
        }
    }

    fun selectRole(role: String) {
        Log.d(TAG, "Role selected: $role")
        updateState { copy(myRole = role, statusMessage = "You are: $role", lastError = null) }
        sendCommand(Command("role", role))
    }

    fun sendCommand(command: Command) {
        val endpointId = connectedEndpointId
        if (endpointId == null) {
            Log.w(TAG, "Cannot send command - not connected")
            return
        }

        try {
            val json = Json.encodeToString(Command.serializer(), command)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))

            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Command sent successfully: ${command.action}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send command: ${command.action}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing command", e)
        }
    }

    fun sendAudioFile(audioFile: File) {
        val endpointId = connectedEndpointId
        if (endpointId == null) {
            Log.e(TAG, "Cannot send file - not connected")
            updateState { copy(lastError = "Not connected to any device") }
            return
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(
                TAG,
                "Audio file invalid: exists=${audioFile.exists()}, size=${audioFile.length()}"
            )
            updateState { copy(lastError = "Audio file is invalid or empty") }
            return
        }

        Log.d(
            TAG,
            "Preparing to send audio file: ${audioFile.name}, size: ${audioFile.length()} bytes"
        )
        updateState {
            copy(
                isSendingFile = true,
                transferProgress = "Preparing file...",
                lastError = null
            )
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Verify file is readable
                if (!audioFile.canRead()) {
                    throw IOException("Cannot read audio file")
                }

                // Create file payload
                val filePayload = Payload.fromFile(audioFile)
                val payloadId = filePayload.id

                Log.d(TAG, "Created file payload with ID: $payloadId")

                // Store transfer info
                pendingFiles[payloadId] = FileTransfer(
                    filename = audioFile.name,
                    expectedSize = audioFile.length()
                )

                // Send file first
                connectionsClient.sendPayload(endpointId, filePayload)
                    .addOnSuccessListener {
                        Log.d(TAG, "File payload sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send file payload", e)
                        launch(Dispatchers.Main) {
                            updateState {
                                copy(
                                    isSendingFile = false,
                                    transferProgress = "",
                                    lastError = "Failed to send file: ${e.message}"
                                )
                            }
                        }
                    }

                // Small delay then send metadata
                delay(100)

                val metadata = "AUDIO_FILE_META:${audioFile.name}:${audioFile.length()}:$payloadId"
                val metadataPayload =
                    Payload.fromBytes(metadata.toByteArray(StandardCharsets.UTF_8))

                connectionsClient.sendPayload(endpointId, metadataPayload)
                    .addOnSuccessListener {
                        Log.d(TAG, "Metadata sent successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send metadata", e)
                    }

                launch(Dispatchers.Main) {
                    updateState { copy(transferProgress = "Sending ${audioFile.name}...") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare file transfer", e)
                launch(Dispatchers.Main) {
                    updateState {
                        copy(
                            isSendingFile = false,
                            transferProgress = "",
                            lastError = "Transfer preparation failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${info.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection", e)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connection established with: $endpointId")
                    connectedEndpointId = endpointId
                    updateState {
                        copy(
                            isConnected = true,
                            statusMessage = "Connected! Choose your role.",
                            lastError = null
                        )
                    }
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected")
                    updateState {
                        copy(
                            statusMessage = "Connection rejected",
                            lastError = "Remote device rejected connection"
                        )
                    }
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error")
                    updateState {
                        copy(
                            statusMessage = "Connection failed",
                            lastError = "Connection error occurred"
                        )
                    }
                }

                else -> {
                    Log.w(TAG, "Connection failed with status: ${result.status.statusCode}")
                    updateState {
                        copy(
                            statusMessage = "Connection failed",
                            lastError = "Unknown connection error"
                        )
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            connectedEndpointId = null
            pendingFiles.clear()
            updateState {
                copy(
                    isConnected = false,
                    myRole = "",
                    statusMessage = "Disconnected - Ready to reconnect",
                    isRecording = false,
                    isSendingFile = false,
                    transferProgress = "",
                    lastError = null
                )
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Device found: ${info.endpointName} ($endpointId)")
            val deviceInfo = "${info.endpointName}|$endpointId"
            val currentDevices = _state.value.discoveredDevices.toMutableList()
            if (!currentDevices.contains(deviceInfo)) {
                currentDevices.add(deviceInfo)
                updateState { copy(discoveredDevices = currentDevices) }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Device lost: $endpointId")
            val currentDevices = _state.value.discoveredDevices.toMutableList()
            currentDevices.removeAll { it.contains(endpointId) }
            updateState { copy(discoveredDevices = currentDevices) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Payload received - Type: ${payload.type}, ID: ${payload.id}")

            when (payload.type) {
                Payload.Type.BYTES -> {
                    handleBytesPayload(payload)
                }

                Payload.Type.FILE -> {
                    Log.d(TAG, "File payload received with ID: ${payload.id}")
                    receiveAudioFile(payload)
                }

                else -> {
                    Log.w(TAG, "Unknown payload type: ${payload.type}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val transfer = pendingFiles[update.payloadId]

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    if (update.totalBytes > 0) {
                        val progress = (update.bytesTransferred * 100 / update.totalBytes).toInt()
                        val filename = transfer?.filename ?: "file"
                        val progressText = "Transferring $filename: $progress%"

                        // Throttle UI updates
                        if (progress % 5 == 0) { // Update every 5%
                            updateState { copy(transferProgress = progressText) }
                        }
                    }
                }

                PayloadTransferUpdate.Status.SUCCESS -> {
                    val filename = transfer?.filename ?: "file"
                    Log.d(TAG, "Transfer completed successfully: $filename")

                    pendingFiles.remove(update.payloadId)
                    updateState {
                        copy(
                            isSendingFile = false,
                            transferProgress = "✅ $filename transferred!",
                            lastError = null
                        )
                    }

                    // Clear success message after 3 seconds
                    coroutineScope.launch {
                        delay(3000)
                        updateState { copy(transferProgress = "") }
                    }
                }

                PayloadTransferUpdate.Status.FAILURE -> {
                    val filename = transfer?.filename ?: "file"
                    Log.e(TAG, "Transfer failed: $filename")

                    pendingFiles.remove(update.payloadId)
                    updateState {
                        copy(
                            isSendingFile = false,
                            transferProgress = "",
                            lastError = "❌ Transfer failed: $filename"
                        )
                    }
                }

                PayloadTransferUpdate.Status.CANCELED -> {
                    val filename = transfer?.filename ?: "file"
                    Log.w(TAG, "Transfer canceled: $filename")

                    pendingFiles.remove(update.payloadId)
                    updateState {
                        copy(
                            isSendingFile = false,
                            transferProgress = "",
                            lastError = "Transfer canceled: $filename"
                        )
                    }
                }
            }
        }
    }

    private fun handleBytesPayload(payload: Payload) {
        try {
            val message = String(payload.asBytes()!!, StandardCharsets.UTF_8)
            Log.d(TAG, "Bytes message received: ${message.take(100)}...")

            when {
                message.startsWith("AUDIO_FILE_META:") -> {
                    val parts = message.split(":")
                    if (parts.size >= 4) {
                        val filename = parts[1]
                        val fileSize = parts[2].toLongOrNull() ?: 0L
                        val payloadId = parts[3].toLongOrNull() ?: 0L

                        Log.d(
                            TAG,
                            "File metadata received - Name: $filename, Size: $fileSize, PayloadID: $payloadId"
                        )

                        if (payloadId > 0) {
                            pendingFiles[payloadId] = FileTransfer(filename, fileSize)
                            updateState { copy(transferProgress = "Receiving $filename...") }
                        }
                    }
                }

                else -> {
                    // Try to parse as command
                    try {
                        val command = Json.decodeFromString(Command.serializer(), message)
                        handleCommand(command)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse as command: $message", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling bytes payload", e)
        }
    }

    private fun receiveAudioFile(payload: Payload) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val receivedDir = File(context.getExternalFilesDir(null), "received")
                if (!receivedDir.exists() && !receivedDir.mkdirs()) {
                    throw IOException("Cannot create received files directory")
                }

                val transfer = pendingFiles[payload.id]
                val filename =
                    transfer?.filename ?: "received_audio_${System.currentTimeMillis()}.mp4"
                val receivedFile = File(receivedDir, filename)

                Log.d(TAG, "Saving received file to: ${receivedFile.absolutePath}")

                payload.asFile()?.asParcelFileDescriptor()?.let { pfd ->
                    var totalBytesWritten = 0L

                    try {
                        FileInputStream(pfd.fileDescriptor).use { inputStream ->
                            FileOutputStream(receivedFile).use { outputStream ->
                                val buffer = ByteArray(8192) // 8KB buffer
                                var bytesRead: Int

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesWritten += bytesRead
                                }

                                outputStream.flush()
                                outputStream.fd.sync() // Force write to disk
                            }
                        }
                    } finally {
                        pfd.close()
                    }

                    // Verify file integrity
                    if (receivedFile.exists() && receivedFile.length() > 0) {
                        val expectedSize = transfer?.expectedSize ?: 0L
                        val actualSize = receivedFile.length()

                        Log.d(
                            TAG,
                            "File received - Expected: $expectedSize bytes, Actual: $actualSize bytes"
                        )

                        if (expectedSize > 0 && actualSize != expectedSize) {
                            Log.w(
                                TAG,
                                "File size mismatch - expected $expectedSize, got $actualSize"
                            )
                        }

                        // Update state on main thread
                        launch(Dispatchers.Main) {
                            updateState {
                                copy(
                                    receivedAudioFile = receivedFile,
                                    transferProgress = "✅ ${receivedFile.name} received!",
                                    lastError = null
                                )
                            }
                        }

                        Log.d(TAG, "File received successfully: ${receivedFile.name}")
                    } else {
                        throw IOException("File was not written correctly")
                    }
                } ?: throw IOException("ParcelFileDescriptor is null")

            } catch (e: Exception) {
                Log.e(TAG, "Error receiving file", e)
                launch(Dispatchers.Main) {
                    updateState {
                        copy(
                            transferProgress = "",
                            lastError = "❌ File reception failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun handleCommand(command: Command) {
        Log.d(TAG, "Command received: ${command.action} - ${command.data}")

        when (command.action) {
            "role" -> {
                updateState { copy(statusMessage = "Remote device is: ${command.data}") }
            }

            "record" -> {
                when (command.data) {
                    "start" -> {
                        Log.d(TAG, "Recording start command received")
                        updateState {
                            copy(
                                isRecording = true,
                                statusMessage = "Recording started by remote device"
                            )
                        }
                    }

                    "stop" -> {
                        Log.d(TAG, "Recording stop command received")
                        updateState {
                            copy(
                                isRecording = false,
                                statusMessage = "Recording stopped by remote device"
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateRecordingState(isRecording: Boolean) {
        Log.d(TAG, "Recording state updated locally: $isRecording")
        updateState { copy(isRecording = isRecording) }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        pendingFiles.clear()
        updateState {
            copy(
                isConnected = false,
                myRole = "",
                statusMessage = "Disconnected - Ready to connect",
                isRecording = false,
                isSendingFile = false,
                transferProgress = "",
                discoveredDevices = emptyList(),
                receivedAudioFile = null,
                recordedVideoFile = null,
                lastError = null
            )
        }
    }

    fun updateState(update: AppState.() -> AppState) {
        _state.value = _state.value.update()
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up connection manager...")
        coroutineScope.cancel()
        disconnect()
    }

    // Debug methods
    fun getConnectionDiagnostics(): String {
        return buildString {
            appendLine("=== Connection Diagnostics ===")
            appendLine("Connected endpoint: $connectedEndpointId")
            appendLine("Pending transfers: ${pendingFiles.size}")
            appendLine("State: ${_state.value}")
            appendLine("External files dir: ${context.getExternalFilesDir(null)}")
        }
    }
}

// ============================================================================
// IMPROVED AUDIO MANAGER
// ============================================================================

class AudioManager(private val context: Context) {
    private val TAG = "AudioManager"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    var onAudioFileReady: ((File) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    private val audioDir by lazy {
        File(context.getExternalFilesDir(null), "audio").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Audio directory created: $created, path: $absolutePath")
            }
        }
    }

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        Log.d(TAG, "Starting audio recording...")

        try {
            cleanup() // Ensure clean state

            val timestamp = System.currentTimeMillis()
            val audioFile = File(audioDir, "audio_${timestamp}.mp4")
            currentAudioFile = audioFile

            Log.d(TAG, "Recording to file: ${audioFile.absolutePath}")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000) // 64kbps for good quality/size balance
                setAudioSamplingRate(22050)   // 22kHz sampling rate
                setOutputFile(audioFile.absolutePath)

                setOnErrorListener { mr, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    val errorMsg = "Recording error: $what/$extra"
                    onRecordingError?.invoke(errorMsg)
                    cleanup()
                }

                setOnInfoListener { mr, what, extra ->
                    Log.d(TAG, "MediaRecorder info: what=$what, extra=$extra")
                }

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Audio recording started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MediaRecorder", e)
                    cleanup()
                    throw e
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            onRecordingError?.invoke("Failed to start recording: ${e.message}")
            releaseMediaRecorder()
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            return
        }

        Log.d(TAG, "Stopping audio recording...")

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                    Log.d(TAG, "MediaRecorder stopped successfully")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "MediaRecorder stop failed (likely short recording)", e)
                    // Don't throw here - file might still be valid
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Process the recorded file
            currentAudioFile?.let { file ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Wait for file system to sync
                        delay(500)

                        if (validateAudioFile(file)) {
                            Log.d(
                                TAG,
                                "Audio file validated successfully: ${file.name}, size: ${file.length()} bytes"
                            )

                            // Notify on main thread
                            withContext(Dispatchers.Main) {
                                onAudioFileReady?.invoke(file)
                            }
                        } else {
                            Log.e(TAG, "Audio file validation failed")
                            withContext(Dispatchers.Main) {
                                onRecordingError?.invoke("Recorded file is invalid or too small")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio file", e)
                        withContext(Dispatchers.Main) {
                            onRecordingError?.invoke("Error processing audio: ${e.message}")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onRecordingError?.invoke("Error stopping recording: ${e.message}")
            releaseMediaRecorder()
        }
    }

    private fun validateAudioFile(file: File): Boolean {
        return file.exists() &&
                file.length() > 1024 && // At least 1KB
                file.canRead() &&
                file.parentFile?.exists() == true
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    fun getStatus(): String {
        return when {
            isRecording -> "🎤 Recording audio..."
            currentAudioFile?.exists() == true -> "✅ Ready (${currentAudioFile?.name})"
            else -> "⏹ Ready to record"
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up audio manager...")
        releaseMediaRecorder()
    }
}

// ============================================================================
// IMPROVED CAMERA MANAGER
// ============================================================================

class CameraManager(private val context: Context) {
    private val TAG = "CameraManager"
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    private var lastVideoFile: File? = null

    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onVideoFileReady: ((File) -> Unit)? = null

    private val videoDir by lazy {
        File(context.getExternalFilesDir(null), "video").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Video directory created: $created, path: $absolutePath")
            }
        }
    }

    fun getLastVideoFile(): File? = lastVideoFile

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        Log.d(TAG, "Initializing camera...")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        videoCapture
                    )

                    Log.d(TAG, "Camera initialized successfully")

                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording() {
        val videoCapture = this.videoCapture
        if (videoCapture == null) {
            Log.e(TAG, "VideoCapture not initialized")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        Log.d(TAG, "Starting video recording...")

        try {
            val timestamp = System.currentTimeMillis()
            val videoFile = File(videoDir, "video_${timestamp}.mp4")
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                        Log.d(TAG, "Audio enabled for video recording")
                    } else {
                        Log.w(TAG, "Audio permission not granted for video")
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d(TAG, "Video recording started: ${videoFile.name}")
                            onRecordingStateChanged?.invoke(true)
                        }

                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (!recordEvent.hasError()) {
                                Log.d(
                                    TAG,
                                    "Video recording finished successfully: ${videoFile.name}, size: ${videoFile.length()} bytes"
                                )
                                lastVideoFile = videoFile
                                onVideoFileReady?.invoke(videoFile)
                            } else {
                                Log.e(
                                    TAG,
                                    "Video recording finished with error: ${recordEvent.error}"
                                )
                            }
                            onRecordingStateChanged?.invoke(false)
                        }

                        is VideoRecordEvent.Status -> {
                            // Optional: Handle status updates
                            Log.v(TAG, "Video recording status update")
                        }
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            isRecording = false
            onRecordingStateChanged?.invoke(false)
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            return
        }

        Log.d(TAG, "Stopping video recording...")
        recording?.stop()
        recording = null
    }

    fun getStatus(): String {
        return when {
            isRecording -> "🎥 Recording video..."
            lastVideoFile?.exists() == true -> "✅ Ready (${lastVideoFile?.name})"
            else -> "⏹ Ready to record"
        }
    }
}

// ============================================================================
// UI COMPONENTS
// ============================================================================

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

@Composable
fun ConnectionScreen(
    appState: AppState,
    onHost: () -> Unit,
    onSearch: () -> Unit,
    onConnect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Choose connection mode:",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onHost,
                modifier = Modifier.weight(1f)
            ) {
                Text("📡 Host")
            }

            Button(
                onClick = onSearch,
                modifier = Modifier.weight(1f)
            ) {
                Text("🔍 Search")
            }
        }

        if (appState.discoveredDevices.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📱 Found devices:",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    appState.discoveredDevices.forEach { device ->
                        val deviceName = device.split("|")[0]
                        OutlinedButton(
                            onClick = { onConnect(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect to: $deviceName")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(onSelectRole: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Choose your device role:",
            style = MaterialTheme.typography.headlineSmall
        )

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { onSelectRole("camera") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎥 Camera Device", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Records video and controls recording",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSelectRole("mic") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎤 Microphone Device", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Records audio synchronized with camera",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
fun AudioFileCard(
    audioFile: File,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🎵 Audio Ready to Send", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPreview() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎤 ", fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        audioFile.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${formatFileSize(audioFile.length())} • Tap to preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sending...")
                } else {
                    Text("📤 Send to Camera Device")
                }
            }
        }
    }
}

@Composable
fun FileStatusCard(
    videoFile: File?,
    audioFile: File?
) {
    val context = LocalContext.current

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📁 Recorded Files", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            videoFile?.let { file ->
                FileRow(
                    icon = "📹",
                    label = "Video",
                    file = file,
                    onClick = { openMediaFile(context, file) }
                )
            }

            audioFile?.let { file ->
                FileRow(
                    icon = "🎤",
                    label = "Audio",
                    file = file,
                    onClick = { openMediaFile(context, file) }
                )
            }
        }
    }
}

@Composable
fun FileRow(
    icon: String,
    label: String,
    file: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$label: ${file.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatFileSize(file.length())} • Tap to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

fun getMimeType(file: File): String {
    val extension = file.extension
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension.lowercase())
        ?: "application/octet-stream"
}

fun openMediaFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_LONG).show()
        Log.e("FileOpen", "No app to open file: ${file.name}", e)
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
        Log.e("FileOpen", "Error opening file: ${file.name}", e)
    }
}