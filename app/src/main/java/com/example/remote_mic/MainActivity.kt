// ============================================================================
// MINIMAL REMOTE MIC APP - Core functionality only
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
    val pendingAudioFile: File? = null,
    val recordedVideoFile: File? = null,

    val isSendingFile: Boolean = false,
    val transferProgress: String = ""
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
            Toast.makeText(this, "Permissions required for app to work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
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
        connectionManager = ConnectionManager(this)
        audioManager = AudioManager(this)
        cameraManager = CameraManager(this)

        // Connect callbacks
        cameraManager.onRecordingStateChanged = { isRecording ->
            connectionManager.updateRecordingState(isRecording)
            if (isRecording) {
                connectionManager.sendCommand(Command("record", "start"))
            } else {
                connectionManager.sendCommand(Command("record", "stop"))
            }
        }

        cameraManager.onVideoFileReady = { videoFile ->
            connectionManager.setVideoFile(videoFile)
        }

        // CHANGED: Don't auto-send audio, set as pending for preview
        audioManager.onAudioFileReady = { audioFile ->
            connectionManager.updateState { copy(pendingAudioFile = audioFile) }
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
    }



}

// ============================================================================
// CONNECTION MANAGER
// ============================================================================

class ConnectionManager(private val context: Context) {
    private val TAG = "Connection"
    private val SERVICE_ID = "remote_mic_app"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null
    fun setVideoFile(videoFile: File) {
        updateState { copy(recordedVideoFile = videoFile) }
    }
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()


    fun sendPendingAudioFile() {
        _state.value.pendingAudioFile?.let { audioFile ->
            sendAudioFile(audioFile)
            updateState { copy(pendingAudioFile = null) }
        }
    }


    fun startHosting() {
        Log.d(TAG, "Starting to host...")
        updateState { copy(statusMessage = "Hosting...") }

        connectionsClient.startAdvertising(
            "Host_${Build.MODEL}",
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        ).addOnSuccessListener {
            updateState { copy(statusMessage = "Waiting for connection...") }
        }.addOnFailureListener { e ->
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
        )
    }

    fun connectToDevice(endpointId: String) {
        connectionsClient.requestConnection(
            "Client_${Build.MODEL}",
            endpointId,
            connectionCallback
        )
    }

    fun selectRole(role: String) {
        updateState { copy(myRole = role, statusMessage = "You are: $role") }
        sendCommand(Command("role", role))
    }

    fun sendCommand(command: Command) {
        connectedEndpointId?.let { endpointId ->
            val json = Json.encodeToString(Command.serializer(), command)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(endpointId, payload)
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    updateState {
                        copy(isConnected = true, statusMessage = "Connected! Choose role.")
                    }
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                }
                else -> {
                    updateState { copy(statusMessage = "Connection failed") }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            updateState { copy(isConnected = false, myRole = "", statusMessage = "Disconnected") }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val currentDevices = _state.value.discoveredDevices.toMutableList()
            currentDevices.add("${info.endpointName}|$endpointId")
            updateState { copy(discoveredDevices = currentDevices) }
        }

        override fun onEndpointLost(endpointId: String) {
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

                    if (message.startsWith("AUDIO_FILE:")) {
                        updateState { copy(transferProgress = "Receiving audio file...") }
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
                    Log.d(TAG, "Receiving audio file...")

                    payload.asFile()?.let { filePayload ->
                        try {
                            val receivedDir = File(context.getExternalFilesDir(null), "received")
                            receivedDir.mkdirs()

                            val receivedFile = File(
                                receivedDir,
                                "received_audio_${System.currentTimeMillis()}.mp4"
                            )

                            // Save file using ParcelFileDescriptor
                            filePayload.asParcelFileDescriptor()?.let { pfd ->
                                FileInputStream(pfd.fileDescriptor).use { inputStream ->
                                    FileOutputStream(receivedFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                        outputStream.flush()
                                    }
                                }
                                pfd.close()
                            }

                            if (receivedFile.exists() && receivedFile.length() > 0) {
                                Log.d(TAG, "File received successfully: ${receivedFile.length()} bytes")
                                    updateState {
                                        copy(
                                            receivedAudioFile = receivedFile,
                                            transferProgress = "Audio received!"
                                        )
                                    }
                            } else {
                                Log.e(TAG, "File reception failed")
                                updateState { copy(transferProgress = "Reception failed") }
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error receiving file", e)
                            updateState { copy(transferProgress = "Reception error") }
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
                    updateState { copy(isSendingFile = false, transferProgress = "Transfer completed!") }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    updateState { copy(isSendingFile = false, transferProgress = "Transfer failed!") }
                }
            }
        }
    }

    private fun handleCommand(command: Command) {
        when (command.action) {
            "role" -> {
                updateState { copy(statusMessage = "Remote device is: ${command.data}") }
            }
            "record" -> {
                when (command.data) {
                    "start" -> updateState {
                        copy(isRecording = true, statusMessage = "Recording started by remote")
                    }
                    "stop" -> updateState {
                        copy(isRecording = false, statusMessage = "Recording stopped by remote")
                    }
                }
            }
        }
    }
    fun sendAudioFile(audioFile: File) {
        connectedEndpointId?.let { endpointId ->
            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "Audio file invalid")
                return
            }

            updateState { copy(isSendingFile = true, transferProgress = "Sending file...") }

            try {
                Log.d(TAG, "Sending audio file: ${audioFile.name}, size: ${audioFile.length()} bytes")

                // Send metadata with more info
                val metadata = "AUDIO_FILE:${audioFile.name}:${audioFile.length()}:${audioFile.extension}"
                val metadataPayload = Payload.fromBytes(metadata.toByteArray(StandardCharsets.UTF_8))
                connectionsClient.sendPayload(endpointId, metadataPayload)

                // Small delay between metadata and file
                Thread.sleep(200)

                // Send file
                val filePayload = Payload.fromFile(audioFile)
                connectionsClient.sendPayload(endpointId, filePayload)

                updateState { copy(transferProgress = "Sending file...") }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send audio file", e)
                updateState { copy(isSendingFile = false, transferProgress = "Send failed: ${e.message}") }
            }
        }
    }

    fun updateRecordingState(isRecording: Boolean) {
        updateState { copy(isRecording = isRecording) }
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
                transferProgress = "",
                discoveredDevices = emptyList(),
                receivedAudioFile = null,
                recordedVideoFile = null
            )
        }
    }

    fun updateState(update: AppState.() -> AppState) {
        _state.value = _state.value.update()
    }
}

// ============================================================================
// AUDIO MANAGER
// ============================================================================
class AudioManager(private val context: Context) {
    private val TAG = "Audio"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    var onAudioFileReady: ((File) -> Unit)? = null

    private val audioDir by lazy {
        File(context.getExternalFilesDir(null), "audio").apply { mkdirs() }
    }

    fun startRecording() {
        if (isRecording) return

        try {
            // Try .mp4 first as it might be more compatible
            val audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.mp4")
            currentAudioFile = audioFile

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
                setAudioEncodingBitRate(96000) // Lower bitrate for compatibility
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)

                // Set max duration to prevent corruption
                setMaxDuration(300000) // 5 minutes max

                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Audio recording started: ${audioFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            cleanup()
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop() // This can throw if recording was too short
                release()
            }
            mediaRecorder = null
            isRecording = false

            currentAudioFile?.let { file ->
                // Small delay to ensure file is written
                Thread.sleep(200)

                if (file.exists() && file.length() > 1024) {
                    Log.d(TAG, "Audio file ready: ${file.length()} bytes")
                    onAudioFileReady?.invoke(file)
                } else {
                    Log.e(TAG, "Audio file too small or missing")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            cleanup()
        }
    }

    private fun cleanup() {
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
            isRecording -> "Recording audio..."
            currentAudioFile != null -> "Ready (${currentAudioFile?.name})"
            else -> "Ready to record"
        }
    }
}


// ============================================================================
// CAMERA MANAGER
// ============================================================================

class CameraManager(private val context: Context) {
    private val TAG = "Camera"
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false

    private var lastVideoFile: File? = null
    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onVideoFileReady: ((File) -> Unit)? = null

    fun getLastVideoFile(): File? = lastVideoFile
    private val videoDir by lazy {
        File(context.getExternalFilesDir(null), "video").apply { mkdirs() }
    }

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )

                Log.d(TAG, "Camera initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        if (isRecording) return

        try {
            val videoFile = File(videoDir, "video_${System.currentTimeMillis()}.mp4")
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            recording = videoCapture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            Log.d(TAG, "Video recording started")
                            onRecordingStateChanged?.invoke(true)
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            if (!recordEvent.hasError()) {
                                Log.d(TAG, "Video recording finished: ${videoFile.length()} bytes")
                                lastVideoFile = videoFile
                                onVideoFileReady?.invoke(videoFile)
                            }
                            onRecordingStateChanged?.invoke(false)
                        }
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun getStatus(): String {
        return if (isRecording) "Recording video..." else "Ready to record"
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
        // Status Card
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = appState.statusMessage,
                    style = MaterialTheme.typography.headlineSmall
                )

                if (appState.transferProgress.isNotEmpty()) {
                    Text(
                        text = appState.transferProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
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
                    connectionManager = connectionManager  // ADD THIS
                )
            }
        }

        // File Status with clickable files
        if (appState.recordedVideoFile != null || appState.receivedAudioFile != null) {
            val context = LocalContext.current

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📁 Recorded Files:", style = MaterialTheme.typography.titleMedium)

                    appState.recordedVideoFile?.let { videoFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openMediaFile(context, videoFile) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📹 ", fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Video: ${videoFile.name}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatFileSize(videoFile.length())} • Tap to open",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    appState.receivedAudioFile?.let { audioFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openMediaFile(context, audioFile) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎤 ", fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Audio: ${audioFile.name}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatFileSize(audioFile.length())} • Tap to open",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Disconnect Button
        if (appState.isConnected) {
            Button(
                onClick = { connectionManager.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect")
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
    cameraManager: CameraManager,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Status
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎥 Camera Mode")
                Text("Recording: ${if (isRecording) "🔴 ACTIVE" else "⚪ INACTIVE"}")
            }
        }

        // Camera Preview
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

        // Controls
        if (isRecording) {
            Button(
                onClick = onStopRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("⏹ Stop Recording")
            }
        } else {
            Button(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏺ Start Recording")
            }
        }
    }
}
@Composable
fun MicrophoneScreen(
    appState: AppState,
    audioManager: AudioManager,
    connectionManager: ConnectionManager // ADD THIS PARAMETER
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
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎤 Microphone Mode")
                Text("Recording: ${if (appState.isRecording) "🔴 ACTIVE" else "⚪ INACTIVE"}")
                Text("Controlled by camera device", style = MaterialTheme.typography.bodySmall)
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
                    text = if (appState.isRecording) "Audio recording..." else "Waiting for camera...",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (appState.isSendingFile) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                    Text("Sending audio file...")
                }
            }
        }

        // NEW: Preview and send section
        appState.pendingAudioFile?.let { audioFile ->
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎵 Audio Ready", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openMediaFile(context, audioFile) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎤 ", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                audioFile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatFileSize(audioFile.length())} • Tap to preview",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { connectionManager.sendPendingAudioFile() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !appState.isSendingFile
                    ) {
                        Text("📤 Send to Camera Device")
                    }
                }
            }
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
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file", Toast.LENGTH_LONG).show()
    }
}