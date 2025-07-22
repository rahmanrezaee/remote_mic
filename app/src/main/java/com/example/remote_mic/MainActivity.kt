// ============================================================================
// COMPLETE FIXED REMOTE MIC APP - With File Testing & Validation
// ============================================================================

package com.example.remote_mic

import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.*

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    val recordedVideoFile: File? = null,
    val isSendingFile: Boolean = false,
    val isReceivingFile: Boolean = false,
    val transferProgress: String = ""
)

data class FilePreviewResults(
    val videoValid: Boolean,
    val videoInfo: String,
    val audioValid: Boolean,
    val audioInfo: String
)

// ============================================================================
// MAIN ACTIVITY
// ============================================================================

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // Managers
    private lateinit var simpleConnectionManager: SimpleConnectionManager
    private lateinit var simpleAudioManager: SimpleAudioManager
    private lateinit var simpleCameraManager: SimpleCameraManager

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permissions granted: $allGranted")
        if (allGranted) {
            initializeApp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "App starting...")

        // Check permissions first
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Add Bluetooth permissions for Android 12+
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

        if (hasAllPermissions) {
            initializeApp()
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    private fun initializeApp() {
        Log.d(TAG, "Initializing app...")

        // Initialize managers
        simpleConnectionManager = SimpleConnectionManager(this)
        simpleAudioManager = SimpleAudioManager(this)
        simpleCameraManager = SimpleCameraManager(this)

        // Connect callbacks
        simpleCameraManager.onRecordingStateChanged = { isRecording ->
            Log.d(TAG, "🎬 Camera recording state changed: $isRecording")
            simpleConnectionManager.updateRecordingState(isRecording)
            if (isRecording) {
                simpleConnectionManager.sendRecordingCommand("start")
            } else {
                simpleConnectionManager.sendRecordingCommand("stop")
            }
        }

        simpleCameraManager.onVideoFileReady = { videoFile ->
            Log.d(TAG, "🎥 Video file ready: ${videoFile.name}")
            simpleConnectionManager.setVideoFile(videoFile)
        }

        simpleAudioManager.onAudioFileReady = { audioFile ->
            Log.d(TAG, "🎤 Audio file ready: ${audioFile.name}")
            simpleConnectionManager.sendAudioFile(audioFile)
        }

        // Set up UI
        setContent {
            MaterialTheme {
                MinimalRemoteMicApp(
                    connectionManager = simpleConnectionManager,
                    audioManager = simpleAudioManager,
                    cameraManager = simpleCameraManager
                )
            }
        }
    }
}

// ============================================================================
// SIMPLE CONNECTION MANAGER - Fixed file reception
// ============================================================================

class SimpleConnectionManager(private val context: Context) {
    private val TAG = "Connection"
    private val SERVICE_ID = "remote_mic_simple"

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun startHosting() {
        Log.d(TAG, "Starting to host...")
        updateState { copy(statusMessage = "Hosting...") }

        connectionsClient.startAdvertising(
            "Host_${Build.MODEL}",
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
            updateState { copy(statusMessage = "Waiting for connection...") }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
            updateState { copy(statusMessage = "Failed to host: ${e.message}") }
        }
    }

    fun startSearching() {
        Log.d(TAG, "Starting to search...")
        updateState { copy(statusMessage = "Searching...") }

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
            updateState { copy(statusMessage = "Failed to search: ${e.message}") }
        }
    }

    fun connectToDevice(endpointId: String) {
        Log.d(TAG, "Connecting to: $endpointId")
        connectionsClient.requestConnection(
            "Client_${Build.MODEL}",
            endpointId,
            connectionCallback
        )
    }

    fun selectRole(role: String) {
        Log.d(TAG, "Selected role: $role")
        updateState { copy(myRole = role, statusMessage = "You are: $role") }
        sendCommand(Command("role", role))
    }

    fun sendRecordingCommand(action: String) {
        Log.d(TAG, "📡 Sending recording command: $action")
        sendCommand(Command("record", action))
    }

    private fun sendCommand(command: Command) {
        connectedEndpointId?.let { endpointId ->
            val json = Json.encodeToString(Command.serializer(), command)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(endpointId, payload)
            Log.d(TAG, "📤 Sent command: $json")
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: ${info.endpointName}")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "✅ Connected!")
                    connectedEndpointId = endpointId
                    updateState {
                        copy(
                            isConnected = true,
                            statusMessage = "Connected! Choose role."
                        )
                    }
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                }

                else -> {
                    Log.e(TAG, "❌ Connection failed: ${result.status}")
                    updateState { copy(statusMessage = "Connection failed") }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected")
            connectedEndpointId = null
            updateState { copy(isConnected = false, myRole = "", statusMessage = "Disconnected") }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Found device: ${info.endpointName}")
            val currentDevices = _state.value.discoveredDevices.toMutableList()
            currentDevices.add("${info.endpointName}|$endpointId")
            updateState { copy(discoveredDevices = currentDevices) }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost device: $endpointId")
            val currentDevices = _state.value.discoveredDevices.toMutableList()
            currentDevices.removeAll { it.contains(endpointId) }
            updateState { copy(discoveredDevices = currentDevices) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val message = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                    Log.d(TAG, "📥 Received: $message")

                    if (message.startsWith("AUDIO_FILE:")) {
                        Log.d(TAG, "📥 Received audio file metadata: $message")
                        updateState {
                            copy(
                                isReceivingFile = true,
                                transferProgress = "Receiving audio file..."
                            )
                        }
                    } else {
                        try {
                            val command = Json.decodeFromString(Command.serializer(), message)
                            handleCommand(command)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse command", e)
                        }
                    }
                }

                Payload.Type.FILE -> {
                    Log.d(TAG, "📥 Receiving audio file...")
                    updateState {
                        copy(
                            isReceivingFile = true,
                            transferProgress = "Downloading file..."
                        )
                    }

                    payload.asFile()?.let { filePayload ->
                        try {
                            val receivedDir = File(context.getExternalFilesDir(null), "received")
                            if (!receivedDir.exists()) {
                                val created = receivedDir.mkdirs()
                                Log.d(TAG, "Created received directory: $created")
                            }

                            val receivedFile = File(
                                receivedDir,
                                "received_audio_${System.currentTimeMillis()}.mp4"
                            )

                            Log.d(TAG, "Saving to: ${receivedFile.absolutePath}")

                            // FIXED: Use ParcelFileDescriptor for Nearby Connections
                            var success = false
                            var totalBytesWritten = 0L

                            // Method 1: ParcelFileDescriptor (most reliable for Nearby Connections)
                            filePayload.asParcelFileDescriptor()?.let { pfd ->
                                Log.d(TAG, "Using ParcelFileDescriptor method")
                                try {
                                    FileInputStream(pfd.fileDescriptor).use { inputStream ->
                                        FileOutputStream(receivedFile).use { outputStream ->
                                            val buffer = ByteArray(8192)
                                            var bytesRead: Int

                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                outputStream.write(buffer, 0, bytesRead)
                                                totalBytesWritten += bytesRead
                                            }

                                            outputStream.flush()
                                            outputStream.fd.sync() // Force write to disk
                                        }
                                    }
                                    pfd.close()
                                    success = totalBytesWritten > 0
                                    Log.d(TAG, "ParcelFileDescriptor: $totalBytesWritten bytes written")
                                } catch (e: Exception) {
                                    Log.e(TAG, "ParcelFileDescriptor method failed", e)
                                    try { pfd.close() } catch (_: Exception) {}
                                }
                            }

                            // Method 2: URI method (fallback)
                            if (!success) {
                                filePayload.asUri()?.let { uri ->
                                    Log.d(TAG, "Fallback to URI method")
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                            FileOutputStream(receivedFile).use { outputStream ->
                                                val buffer = ByteArray(8192)
                                                var bytesRead: Int

                                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                    outputStream.write(buffer, 0, bytesRead)
                                                    totalBytesWritten += bytesRead
                                                }

                                                outputStream.flush()
                                                outputStream.fd.sync()
                                            }
                                        }
                                        success = totalBytesWritten > 0
                                        Log.d(TAG, "URI method: $totalBytesWritten bytes written")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "URI method failed", e)
                                    }
                                }
                            }

                            // Method 3: Last resort - simple URI copy
                            if (!success) {
                                filePayload.asUri()?.let { uri ->
                                    Log.d(TAG, "Last resort: Simple URI copy")
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                            FileOutputStream(receivedFile).use { outputStream ->
                                                totalBytesWritten = inputStream.copyTo(outputStream)
                                                outputStream.flush()
                                                outputStream.fd.sync()
                                            }
                                        }
                                        success = totalBytesWritten > 0
                                        Log.d(TAG, "Simple URI copy: $totalBytesWritten bytes written")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Simple URI copy failed", e)
                                    }
                                }
                            }

                            // Validate result
                            if (success && receivedFile.exists() && receivedFile.length() > 0) {
                                // Add small delay for file system sync
                                Thread.sleep(200)

                                Log.d(TAG, "✅ File received successfully!")
                                Log.d(TAG, "Final file size: ${receivedFile.length()} bytes")
                                Log.d(TAG, "File readable: ${receivedFile.canRead()}")

                                // Validate file header
                                validateAudioFile(receivedFile)

                                updateState {
                                    copy(
                                        receivedAudioFile = receivedFile,
                                        isReceivingFile = false,
                                        transferProgress = "Audio received: ${formatFileSize(receivedFile.length())}"
                                    )
                                }
                            } else {
                                Log.e(TAG, "❌ File reception failed - no bytes written or file missing")
                                if (receivedFile.exists()) receivedFile.delete()
                                updateState {
                                    copy(
                                        isReceivingFile = false,
                                        transferProgress = "Reception failed: No data"
                                    )
                                }
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Exception in file reception", e)
                            updateState {
                                copy(
                                    isReceivingFile = false,
                                    transferProgress = "Reception error: ${e.message}"
                                )
                            }
                        }
                    } ?: run {
                        Log.e(TAG, "❌ FilePayload is null")
                        updateState {
                            copy(
                                isReceivingFile = false,
                                transferProgress = "Error: Invalid file payload"
                            )
                        }
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val progress = (update.bytesTransferred * 100 / update.totalBytes).toInt()
                    updateState { copy(transferProgress = "Transfer: $progress%") }
                }

                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "✅ File transfer completed successfully")
                    updateState {
                        copy(
                            isSendingFile = false,
                            transferProgress = "Transfer completed!"
                        )
                    }
                }

                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.e(TAG, "❌ File transfer failed")
                    updateState {
                        copy(
                            isSendingFile = false,
                            isReceivingFile = false,
                            transferProgress = "Transfer failed!"
                        )
                    }
                }
            }
        }
    }

    private fun validateAudioFile(file: File): Boolean {
        try {
            Log.d(TAG, "🔍 Validating audio file...")

            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "File doesn't exist or isn't readable")
                return false
            }

            if (file.length() < 1024) { // Less than 1KB is suspicious
                Log.e(TAG, "File too small: ${file.length()} bytes")
                return false
            }

            // Check MP4 file signature
            file.inputStream().buffered().use { input ->
                val header = ByteArray(12)
                val bytesRead = input.read(header)

                if (bytesRead >= 8) {
                    // Check for MP4 container signature at offset 4-7
                    val ftyp = String(header.sliceArray(4..7), Charsets.ISO_8859_1)
                    Log.d(TAG, "File signature: '$ftyp'")

                    val isValidMp4 = ftyp == "ftyp"
                    Log.d(TAG, "Valid MP4 signature: $isValidMp4")
                    return isValidMp4
                }
            }

            Log.e(TAG, "Could not read file header")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error validating file", e)
            return false
        }
    }

    private fun handleCommand(command: Command) {
        Log.d(TAG, "📨 Handling command: ${command.action} - ${command.data}")
        when (command.action) {
            "role" -> {
                updateState { copy(statusMessage = "Remote device is: ${command.data}") }
            }

            "record" -> {
                when (command.data) {
                    "start" -> {
                        Log.d(TAG, "📥 Received START command - syncing recording state")
                        updateState {
                            copy(
                                isRecording = true,
                                statusMessage = "Recording started by remote"
                            )
                        }
                    }

                    "stop" -> {
                        Log.d(TAG, "📥 Received STOP command - syncing recording state")
                        updateState {
                            copy(
                                isRecording = false,
                                statusMessage = "Recording stopped by remote"
                            )
                        }
                    }
                }
            }
        }
    }

    fun sendAudioFile(audioFile: File) {
        connectedEndpointId?.let { endpointId ->
            Log.d(TAG, "📤 Sending audio file: ${audioFile.name}")
            Log.d(TAG, "File path: ${audioFile.absolutePath}")
            Log.d(TAG, "File size: ${audioFile.length()} bytes")
            Log.d(TAG, "File exists: ${audioFile.exists()}")
            Log.d(TAG, "File readable: ${audioFile.canRead()}")

            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Audio file doesn't exist!")
                updateState { copy(transferProgress = "Error: Audio file not found") }
                return
            }

            if (audioFile.length() == 0L) {
                Log.e(TAG, "❌ Audio file is empty!")
                updateState { copy(transferProgress = "Error: Audio file is empty") }
                return
            }

            updateState { copy(isSendingFile = true, transferProgress = "Preparing to send...") }

            try {
                // Send file metadata first
                val metadata = "AUDIO_FILE:${audioFile.name}:${audioFile.length()}"
                val metadataPayload =
                    Payload.fromBytes(metadata.toByteArray(StandardCharsets.UTF_8))
                connectionsClient.sendPayload(endpointId, metadataPayload)

                // Send actual file
                val filePayload = Payload.fromFile(audioFile)
                connectionsClient.sendPayload(endpointId, filePayload)

                Log.d(TAG, "📤 Audio file sending started: ${audioFile.name}")
                updateState { copy(transferProgress = "Sending file...") }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send audio file", e)
                updateState {
                    copy(
                        isSendingFile = false,
                        transferProgress = "Failed to send file: ${e.message}"
                    )
                }
            }
        } ?: run {
            Log.e(TAG, "❌ No connected endpoint to send file to")
            updateState { copy(transferProgress = "Error: No connected device") }
        }
    }

    fun updateRecordingState(isRecording: Boolean) {
        Log.d(TAG, "🔄 Updating local recording state to: $isRecording")
        updateState { copy(isRecording = isRecording) }
    }

    fun setVideoFile(videoFile: File) {
        updateState { copy(recordedVideoFile = videoFile) }
    }

    fun disconnect() {
        connectionsClient.stopAllEndpoints()
        updateState {
            copy(
                isConnected = false,
                myRole = "",
                statusMessage = "Ready to connect",
                isRecording = false,
                isSendingFile = false,
                isReceivingFile = false,
                transferProgress = ""
            )
        }
    }

    private fun updateState(update: AppState.() -> AppState) {
        _state.value = _state.value.update()
    }
}

// ============================================================================
// SIMPLE AUDIO MANAGER - Fixed with better error handling
// ============================================================================

class SimpleAudioManager(private val context: Context) {
    private val TAG = "Audio"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    var onAudioFileReady: ((File) -> Unit)? = null

    private val audioDir by lazy {
        File(context.getExternalFilesDir(null), "audio").apply {
            mkdirs()
            Log.d(TAG, "Audio directory: ${this.absolutePath}")
        }
    }

    fun startRecording() {
        Log.d(TAG, "🎤 startRecording() called")

        if (isRecording) {
            Log.w(TAG, "⚠️ Already recording!")
            return
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ No audio permission!")
            return
        }

        Log.d(TAG, "🎤 Starting audio recording...")
        try {
            val audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.mp4")
            currentAudioFile = audioFile
            Log.d(TAG, "Audio file: ${audioFile.absolutePath}")

            // Clean up any existing recorder
            mediaRecorder?.release()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)

                Log.d(TAG, "MediaRecorder configured, preparing...")
                prepare()
                Log.d(TAG, "MediaRecorder prepared, starting...")
                start()
                Log.d(TAG, "✅ MediaRecorder started!")
            }

            isRecording = true
            Log.d(TAG, "✅ Audio recording started: ${audioFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio recording", e)
            cleanupRecorder()
        }
    }

    fun stopRecording() {
        Log.d(TAG, "🛑 stopRecording() called")

        if (!isRecording) {
            Log.w(TAG, "⚠️ Not recording!")
            return
        }

        Log.d(TAG, "🛑 Stopping audio recording...")
        try {
            mediaRecorder?.let { recorder ->
                try {
                    Log.d(TAG, "Calling MediaRecorder.stop()...")
                    recorder.stop()
                    Log.d(TAG, "✅ MediaRecorder stopped")
                } catch (e: RuntimeException) {
                    Log.e(TAG, "MediaRecorder.stop() failed (may be in invalid state)", e)
                    // Continue with cleanup even if stop failed
                }

                Log.d(TAG, "Releasing MediaRecorder...")
                recorder.release()
                Log.d(TAG, "✅ MediaRecorder released")
            }

            mediaRecorder = null
            isRecording = false

            currentAudioFile?.let { file ->
                // Give MediaRecorder time to finish writing
                Thread.sleep(500)

                Log.d(TAG, "📁 Checking recorded file...")
                Log.d(TAG, "File: ${file.name}")
                Log.d(TAG, "Path: ${file.absolutePath}")
                Log.d(TAG, "Exists: ${file.exists()}")
                Log.d(TAG, "Size: ${file.length()} bytes")
                Log.d(TAG, "Readable: ${file.canRead()}")

                if (file.exists() && file.length() > 1024) { // At least 1KB
                    Log.d(TAG, "📤 Audio file ready for sending")
                    onAudioFileReady?.invoke(file)
                } else {
                    Log.e(TAG, "❌ Audio file is too small or doesn't exist")
                    if (file.exists() && file.length() == 0L) {
                        Log.e(TAG, "File exists but is empty - MediaRecorder may have failed")
                    } else {
                        Log.e(TAG, "File does not exist - MediaRecorder may not have written it")
                    }
                }
            } ?: Log.e(TAG, "❌ currentAudioFile is null")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in stopRecording", e)
            cleanupRecorder()
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    fun getRecordingState(): String {
        return when {
            isRecording -> "Recording audio"
            currentAudioFile != null -> "Ready (last: ${currentAudioFile?.name})"
            else -> "Ready to record"
        }
    }
}

// ============================================================================
// SIMPLE CAMERA MANAGER - Fixed immediate sync
// ============================================================================

class SimpleCameraManager(private val context: Context) {
    private val TAG = "Camera"
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    private var cameraInitialized = false

    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onVideoFileReady: ((File) -> Unit)? = null

    private val videoDir by lazy {
        File(context.getExternalFilesDir(null), "video").apply {
            mkdirs()
            Log.d(TAG, "Video directory: ${this.absolutePath}")
        }
    }

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        Log.d(TAG, "Initializing camera...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "Camera provider obtained")

                // Build preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Build recorder
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind and bind
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                cameraInitialized = true
                Log.d(TAG, "✅ Camera initialized successfully!")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Camera initialization failed", e)
                cameraInitialized = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(): String? {
        Log.d(TAG, "🎬 startRecording() called")

        if (!cameraInitialized) {
            Log.e(TAG, "❌ Camera not initialized yet!")
            return null
        }

        val videoCapture = this.videoCapture
        if (videoCapture == null) {
            Log.e(TAG, "❌ VideoCapture is null!")
            return null
        }

        if (isRecording) {
            Log.w(TAG, "⚠️ Already recording!")
            return null
        }

        Log.d(TAG, "🎬 Starting video recording...")

        try {
            val videoFile = File(videoDir, "video_${System.currentTimeMillis()}.mp4")
            Log.d(TAG, "Video file: ${videoFile.absolutePath}")

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    // Enable audio if permission granted
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                        Log.d(TAG, "Audio enabled for recording")
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d(TAG, "✅ Video recording STARTED!")
                            // IMMEDIATE CALLBACK - This will trigger command to mic
                            onRecordingStateChanged?.invoke(true)
                        }

                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (!recordEvent.hasError()) {
                                Log.d(TAG, "✅ Video recording FINISHED: ${videoFile.name}")
                                Log.d(TAG, "File size: ${videoFile.length()} bytes")

                                // IMMEDIATE CALLBACK - This will trigger command to mic
                                onRecordingStateChanged?.invoke(false)
                                onVideoFileReady?.invoke(videoFile)
                            } else {
                                Log.e(TAG, "❌ Recording error: ${recordEvent.error}")
                                onRecordingStateChanged?.invoke(false)
                            }
                        }
                    }
                }

            Log.d(TAG, "Recording started, waiting for callback...")
            return videoFile.name

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            return null
        }
    }

    fun stopRecording() {
        Log.d(TAG, "🛑 stopRecording() called")

        recording?.let {
            Log.d(TAG, "🛑 Stopping recording...")
            it.stop()
            recording = null
        } ?: Log.w(TAG, "⚠️ No active recording to stop")
    }

    fun getRecordingState(): String {
        return when {
            !cameraInitialized -> "Camera not ready"
            isRecording -> "Recording active"
            else -> "Ready to record"
        }
    }
}

// ============================================================================
// AUDIO-VIDEO MERGER CLASS
// ============================================================================

class AudioVideoMerger(private val context: Context) {
    companion object {
        private const val TAG = "AudioVideoMerger"
    }

    fun mergeAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFileName: String = "merged_${System.currentTimeMillis()}.mp4",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🎬 Starting merge process...")

                // Validate files
                withContext(Dispatchers.Main) { onProgress?.invoke("Validating files...") }

                if (!videoFile.exists() || videoFile.length() == 0L) {
                    Log.e(TAG, "❌ Video file invalid")
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                if (!audioFile.exists() || audioFile.length() == 0L) {
                    Log.e(TAG, "❌ Audio file invalid")
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                // Create output directory
                val outputDir = File(context.getExternalFilesDir(null), "merged")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                // Delete existing output file if present
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                Log.d(TAG, "📁 Files ready for merge:")
                Log.d(TAG, "Video: ${videoFile.absolutePath} (${videoFile.length()} bytes)")
                Log.d(TAG, "Audio: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                Log.d(TAG, "Output: ${outputFile.absolutePath}")

                withContext(Dispatchers.Main) { onProgress?.invoke("Merging files...") }

                // Switch to main thread for FFmpeg execution
                withContext(Dispatchers.Main) {
                    performMerge(videoFile, audioFile, outputFile, onComplete)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in merge process", e)
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }

    private fun performMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onComplete: (File?) -> Unit
    ) {
        // FIXED: Better FFmpeg command for audio sync
        val command = buildString {
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")
            append("-c:v copy ")  // Copy video stream as-is
            append("-c:a aac ")   // Re-encode audio as AAC
            append("-b:a 128k ")  // Audio bitrate
            append("-map 0:v:0 ") // Map first video stream from first input
            append("-map 1:a:0 ") // Map first audio stream from second input
            append("-shortest ")  // End when shortest stream ends
            append("-avoid_negative_ts make_zero ") // Fix timestamp issues
            append("-y ")         // Overwrite output file
            append("\"${outputFile.absolutePath}\"")
        }

        Log.d(TAG, "🎬 FFmpeg command: $command")

        try {
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                val output = session.output

                Log.d(TAG, "🎬 FFmpeg execution completed")
                Log.d(TAG, "Return code: $returnCode")
                Log.d(TAG, "Session state: ${session.state}")

                if (ReturnCode.isSuccess(returnCode)) {
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "✅ Merge successful!")
                        Log.d(TAG, "Output file: ${outputFile.absolutePath}")
                        Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                        onComplete(outputFile)
                    } else {
                        Log.e(TAG, "❌ Output file is empty or doesn't exist")
                        Log.e(TAG, "FFmpeg output: $output")
                        onComplete(null)
                    }
                } else {
                    Log.e(TAG, "❌ FFmpeg failed with return code: $returnCode")
                    Log.e(TAG, "FFmpeg output: $output")

                    // Try alternative command
                    tryAlternativeMerge(videoFile, audioFile, outputFile, onComplete)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during FFmpeg execution", e)
            onComplete(null)
        }
    }

    private fun tryAlternativeMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onComplete: (File?) -> Unit
    ) {
        Log.d(TAG, "🔄 Trying alternative merge method...")

        // Delete the failed output file
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Alternative command - replace audio track entirely
        val altCommand = buildString {
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")
            append("-c:v copy ")
            append("-c:a aac ")
            append("-map 0:v ")
            append("-map 1:a ")
            append("-shortest ")
            append("-y ")
            append("\"${outputFile.absolutePath}\"")
        }

        Log.d(TAG, "🎬 Alternative FFmpeg command: $altCommand")

        FFmpegKit.executeAsync(altCommand) { session ->
            val returnCode = session.returnCode
            val output = session.output

            if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Alternative merge successful!")
                Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                onComplete(outputFile)
            } else {
                Log.e(TAG, "❌ Alternative merge also failed")
                Log.e(TAG, "Return code: $returnCode")
                Log.e(TAG, "Output: $output")
                onComplete(null)
            }
        }
    }
}

// ============================================================================
// FILE VALIDATION FUNCTIONS
// ============================================================================

fun validateBothFiles(
    context: Context,
    videoFile: File?,
    audioFile: File?,
    onResult: (FilePreviewResults) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        Log.d("FileValidation", "=== VALIDATING BOTH FILES ===")

        val videoResults = videoFile?.let { validateVideoFile(it) }
            ?: Pair(false, "No video file")

        val audioResults = audioFile?.let { validateAudioFile(it) }
            ?: Pair(false, "No audio file")

        val results = FilePreviewResults(
            videoValid = videoResults.first,
            videoInfo = videoResults.second,
            audioValid = audioResults.first,
            audioInfo = audioResults.second
        )

        Log.d("FileValidation", "Video Valid: ${results.videoValid} - ${results.videoInfo}")
        Log.d("FileValidation", "Audio Valid: ${results.audioValid} - ${results.audioInfo}")

        withContext(Dispatchers.Main) {
            onResult(results)
        }
    }
}

fun validateVideoFile(file: File): Pair<Boolean, String> {
    Log.d("VideoValidation", "Validating video: ${file.name}")

    if (!file.exists()) {
        return Pair(false, "File doesn't exist")
    }

    if (file.length() == 0L) {
        return Pair(false, "File is empty")
    }

    if (file.length() < 1024 * 100) { // Less than 100KB is suspicious for video
        return Pair(false, "File too small (${file.length()} bytes)")
    }

    // Use FFprobe to validate
    val session = FFprobeKit.execute("-v quiet -select_streams v:0 -show_entries stream=codec_name,duration,width,height -of csv=p=0 \"${file.absolutePath}\"")

    return if (ReturnCode.isSuccess(session.returnCode)) {
        val output = session.output?.trim()
        Log.d("VideoValidation", "FFprobe output: $output")

        if (output.isNullOrEmpty()) {
            Pair(false, "No video stream found")
        } else {
            val parts = output.split(",")
            val codec = parts.getOrNull(0) ?: "unknown"
            val duration = parts.getOrNull(1) ?: "unknown"
            val width = parts.getOrNull(2) ?: "unknown"
            val height = parts.getOrNull(3) ?: "unknown"

            Pair(true, "$codec ${width}x${height} ${duration}s")
        }
    } else {
        Log.e("VideoValidation", "FFprobe failed: ${session.output}")
        Pair(false, "Invalid video format")
    }
}

fun validateAudioFile(file: File): Pair<Boolean, String> {
    Log.d("AudioValidation", "Validating audio: ${file.name}")

    if (!file.exists()) {
        Log.e("AudioValidation", "File doesn't exist: ${file.absolutePath}")
        return Pair(false, "File doesn't exist")
    }

    if (file.length() == 0L) {
        Log.e("AudioValidation", "File is empty")
        return Pair(false, "File is empty (0 bytes)")
    }

    if (file.length() < 1024) { // Less than 1KB is suspicious
        Log.e("AudioValidation", "File too small: ${file.length()} bytes")
        return Pair(false, "File too small (${file.length()} bytes)")
    }

    Log.d("AudioValidation", "File exists and has size: ${file.length()} bytes")

    // Check file header first
    try {
        file.inputStream().use { input ->
            val header = ByteArray(12)
            val bytesRead = input.read(header)

            if (bytesRead >= 8) {
                val headerStr = header.joinToString(" ") { "%02x".format(it) }
                Log.d("AudioValidation", "File header: $headerStr")

                // Check for MP4 signature
                val ftyp = String(header.sliceArray(4..7), Charsets.ISO_8859_1)
                if (ftyp != "ftyp") {
                    Log.e("AudioValidation", "Not a valid MP4 file. Header signature: '$ftyp'")
                    return Pair(false, "Invalid MP4 header (got '$ftyp')")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("AudioValidation", "Error reading file header", e)
        return Pair(false, "Can't read file: ${e.message}")
    }

    // Use FFprobe to validate audio streams
    val command = "-v quiet -select_streams a:0 -show_entries stream=codec_name,duration,bit_rate,sample_rate -of csv=p=0 \"${file.absolutePath}\""
    Log.d("AudioValidation", "FFprobe command: $command")

    val session = FFprobeKit.execute(command)

    return if (ReturnCode.isSuccess(session.returnCode)) {
        val output = session.output?.trim()
        Log.d("AudioValidation", "FFprobe success. Output: '$output'")

        if (output.isNullOrEmpty()) {
            Log.e("AudioValidation", "No audio stream found in file")
            Pair(false, "No audio stream detected")
        } else {
            val parts = output.split(",")
            val codec = parts.getOrNull(0) ?: "unknown"
            val duration = parts.getOrNull(1) ?: "unknown"
            val bitrate = parts.getOrNull(2) ?: "unknown"
            val sampleRate = parts.getOrNull(3) ?: "unknown"

            val info = "$codec ${duration}s ${bitrate}bps ${sampleRate}Hz"
            Log.d("AudioValidation", "Audio info: $info")
            Pair(true, info)
        }
    } else {
        Log.e("AudioValidation", "FFprobe failed")
        Log.e("AudioValidation", "Return code: ${session.returnCode}")
        Log.e("AudioValidation", "Output: ${session.output}")
        Log.e("AudioValidation", "Error: ${session.failStackTrace}")
        Pair(false, "FFprobe validation failed")
    }
}

fun testVideoFile(context: Context, file: File) {
    Log.d("VideoTest", "=== TESTING VIDEO FILE ===")
    Log.d("VideoTest", "File: ${file.absolutePath}")
    Log.d("VideoTest", "Size: ${file.length()} bytes")

    CoroutineScope(Dispatchers.IO).launch {
        val result = validateVideoFile(file)
        Log.d("VideoTest", "Result: ${result.first} - ${result.second}")

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Video: ${if (result.first) "✅ Valid" else "❌ Invalid"} - ${result.second}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

fun testAudioFile(context: Context, file: File) {
    Log.d("AudioTest", "=== TESTING AUDIO FILE ===")
    Log.d("AudioTest", "File: ${file.absolutePath}")
    Log.d("AudioTest", "Size: ${file.length()} bytes")
    Log.d("AudioTest", "Exists: ${file.exists()}")
    Log.d("AudioTest", "Readable: ${file.canRead()}")

    CoroutineScope(Dispatchers.IO).launch {
        val result = validateAudioFile(file)
        Log.d("AudioTest", "Result: ${result.first} - ${result.second}")

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Audio: ${if (result.first) "✅ Valid" else "❌ Invalid"} - ${result.second}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

fun mergeMediaFiles(context: Context, videoFile: File, audioFile: File) {
    Log.d("MergeFiles", "=== STARTING MERGE ===")

    val merger = AudioVideoMerger(context)
    merger.mergeAudioVideo(
        videoFile = videoFile,
        audioFile = audioFile,
        onProgress = { progress ->
            Log.d("MergeFiles", "Progress: $progress")
        },
        onComplete = { mergedFile ->
            if (mergedFile != null) {
                Log.d("MergeFiles", "✅ Merge successful: ${mergedFile.absolutePath}")

                try {
                    openMediaFile(context, mergedFile)
                } catch (e: Exception) {
                    Log.e("MergeFiles", "Failed to open merged file", e)
                    Toast.makeText(context, "Merge successful but can't open file", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("MergeFiles", "❌ Merge failed")
                Toast.makeText(context, "Merge failed - check logs", Toast.LENGTH_LONG).show()
            }
        }
    )
}

// Helper function to open a file using FileProvider
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
        Log.e("FileOpen", "No app found to open file: ${file.name}", e)
        Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("FileOpen", "Failed to open file: ${file.name}", e)
        Toast.makeText(context, "Error opening file", Toast.LENGTH_LONG).show()
    }
}

// ============================================================================
// UI COMPONENTS - Enhanced with file testing
// ============================================================================

@Composable
fun MinimalRemoteMicApp(
    connectionManager: SimpleConnectionManager,
    audioManager: SimpleAudioManager,
    cameraManager: SimpleCameraManager
) {
    val appState by connectionManager.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = appState.statusMessage,
                    style = MaterialTheme.typography.headlineSmall
                )

                // Transfer progress
                if (appState.isSendingFile || appState.isReceivingFile || appState.transferProgress.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (appState.isSendingFile || appState.isReceivingFile) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = appState.transferProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    onStartRecording = {
                        Log.d("UI", "🎬 Camera start button pressed")
                        val result = cameraManager.startRecording()
                        if (result == null) {
                            Log.e("UI", "❌ Failed to start camera recording!")
                        }
                    },
                    onStopRecording = {
                        Log.d("UI", "🛑 Camera stop button pressed")
                        cameraManager.stopRecording()
                    }
                )
            }

            appState.myRole == "mic" -> {
                MicrophoneScreen(
                    appState = appState,
                    audioManager = audioManager
                )
            }
        }

        // Enhanced file status and testing
        if (appState.recordedVideoFile != null || appState.receivedAudioFile != null) {
            EnhancedFilesStatusCard(appState)
        }

        // Disconnect button
        if (appState.isConnected) {
            Button(
                onClick = { connectionManager.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect & Reset")
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) {
            Text("Host Connection")
        }

        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
            Text("Search for Devices")
        }

        if (appState.discoveredDevices.isNotEmpty()) {
            Text("Found devices:")
            appState.discoveredDevices.forEach { device ->
                val deviceName = device.split("|")[0]
                Button(
                    onClick = { onConnect(device) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect to: $deviceName")
                }
            }
        }
    }
}

@Composable
fun RoleSelectionScreen(onSelectRole: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose your role:", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = { onSelectRole("camera") }, modifier = Modifier.fillMaxWidth()) {
            Text("🎥 Camera (Video + Control)")
        }

        Button(onClick = { onSelectRole("mic") }, modifier = Modifier.fillMaxWidth()) {
            Text("🎤 Microphone (Audio)")
        }
    }
}

@Composable
fun CameraScreen(
    isRecording: Boolean,
    cameraManager: SimpleCameraManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraStatus by remember { mutableStateOf("Initializing...") }

    LaunchedEffect(Unit) {
        while (true) {
            cameraStatus = cameraManager.getRecordingState()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎥 Camera Status: $cameraStatus")
                Text("Recording: ${if (isRecording) "🔴 ACTIVE" else "⚪ INACTIVE"}")
                if (isRecording) {
                    Text(
                        "📡 Mic device should be recording too",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Camera preview
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraManager.initializeCamera(this, lifecycleOwner)
                }
            }
        )

        // Recording controls
        if (isRecording) {
            Button(
                onClick = onStopRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("⏹ Stop Recording (Both Devices)")
            }
        } else {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏺ Start Recording (Both Devices)")
            }
        }
    }
}

@Composable
fun MicrophoneScreen(
    appState: AppState,
    audioManager: SimpleAudioManager
) {
    val context = LocalContext.current
    var audioStatus by remember { mutableStateOf("Ready") }

    // FIXED: Immediate sync when recording state changes
    LaunchedEffect(appState.isRecording) {
        Log.d("MicrophoneScreen", "🎤 Recording state changed to: ${appState.isRecording}")
        if (appState.isRecording) {
            Log.d("MicrophoneScreen", "🎤 Starting audio recording...")
            audioManager.startRecording()
        } else {
            Log.d("MicrophoneScreen", "🛑 Stopping audio recording...")
            audioManager.stopRecording()
        }
    }

    // Update audio status periodically
    LaunchedEffect(Unit) {
        while (true) {
            audioStatus = audioManager.getRecordingState()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎤 Audio Status: $audioStatus")
                Text("Recording: ${if (appState.isRecording) "🔴 ACTIVE" else "⚪ INACTIVE"}")
                Text("Controlled by camera device", style = MaterialTheme.typography.bodySmall)

                // Transfer status
                if (appState.isSendingFile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sending audio to camera...",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (appState.isRecording) "🎤 RECORDING" else "🎤 READY",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (appState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when {
                        appState.isRecording -> "Audio recording in progress..."
                        appState.isSendingFile -> "Sending audio file..."
                        else -> "Waiting for camera to start..."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                if (appState.isRecording) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ============================================================================
// MISSING FUNCTIONS - Add these to your existing code
// ============================================================================

fun getMimeType(file: File): String {
    val extension = file.extension
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension.lowercase())
        ?: "application/octet-stream"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
fun EnhancedFilesStatusCard(appState: AppState) {
    val context = LocalContext.current
    var showingPreview by remember { mutableStateOf(false) }
    var previewResults by remember { mutableStateOf<FilePreviewResults?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📁 Recorded Files:", style = MaterialTheme.typography.titleMedium)

            // Show individual files
            appState.recordedVideoFile?.let { videoFile ->
                FileItemCard(
                    file = videoFile,
                    icon = "🎥",
                    type = "Video",
                    onTest = { testVideoFile(context, videoFile) },
                    onOpen = { openMediaFile(context, videoFile) }
                )
            }

            appState.receivedAudioFile?.let { audioFile ->
                FileItemCard(
                    file = audioFile,
                    icon = "🎤",
                    type = "Audio",
                    onTest = { testAudioFile(context, audioFile) },
                    onOpen = { openMediaFile(context, audioFile) }
                )
            }

            // Preview both files button
            if (appState.recordedVideoFile != null && appState.receivedAudioFile != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        showingPreview = true
                        validateBothFiles(
                            context = context,
                            videoFile = appState.recordedVideoFile,
                            audioFile = appState.receivedAudioFile
                        ) { results ->
                            previewResults = results
                            showingPreview = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !showingPreview
                ) {
                    if (showingPreview) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Files...")
                    } else {
                        Text("🔍 Test Both Files")
                    }
                }

                // Show preview results
                previewResults?.let { results ->
                    PreviewResultsCard(results = results)
                }

                // Merge button - only enabled if both files are valid
                val canMerge = previewResults?.let {
                    it.videoValid && it.audioValid
                } ?: false

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        mergeMediaFiles(
                            context = context,
                            videoFile = appState.recordedVideoFile!!,
                            audioFile = appState.receivedAudioFile!!
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canMerge
                ) {
                    if (canMerge) {
                        Text("🎬 Merge Audio & Video")
                    } else {
                        Text("❌ Fix Files First")
                    }
                }

                if (!canMerge && previewResults != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "⚠️ Please test files first and fix any issues before merging.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// MISSING UI COMPONENTS - Add these to your existing code
// ============================================================================

@Composable
fun FileItemCard(
    file: File,
    icon: String,
    type: String,
    onTest: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$icon $type: ${file.name}")
                    Text(
                        "Size: ${formatFileSize(file.length())}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Path: ${file.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🧪 Test")
                }

                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📱 Open")
                }
            }
        }
    }
}

@Composable
fun PreviewResultsCard(results: FilePreviewResults) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (results.videoValid && results.audioValid)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📋 File Test Results:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Video results
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (results.videoValid) "✅" else "❌",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Video: ${results.videoInfo}")
            }

            // Audio results
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (results.audioValid) "✅" else "❌",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Audio: ${results.audioInfo}")
            }

            if (!results.videoValid || !results.audioValid) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🔧 Issues found! Check the logs or try recording again.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}