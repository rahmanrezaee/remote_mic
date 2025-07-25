package com.example.remote_mic.managers

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.remote_mic.data.AppState
import com.example.remote_mic.data.Command
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

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


    // Add this method to your ConnectionManager:
    fun discardReceivedFile() {
        val receivedFile = _state.value.receivedAudioFile
        if (receivedFile != null && receivedFile.exists()) {
            try {
                receivedFile.delete()
                Log.d(TAG, "Discarded received file: ${receivedFile.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete received file", e)
            }
        }

        updateState {
            copy(
                receivedAudioFile = null,
                transferProgress = "",
                lastError = null
            )
        }
    }


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

    // Enhanced role selection with automatic assignment
    fun selectRole(role: String) {
        Log.d(TAG, "Role selected: $role")

        // Determine the opposite role for the remote device
        val oppositeRole = when (role) {
            "camera" -> "mic"
            "mic" -> "camera"
            else -> ""
        }

        // Update local state immediately
        updateState {
            copy(
                myRole = role,
                remoteRole = oppositeRole,
                statusMessage = "You are: $role",
                lastError = null
            )
        }

        // Send role assignment command to remote device
        sendCommand(Command("role_assignment", "$role:$oppositeRole"))

        Log.d(TAG, "Local role: $role, Remote role will be: $oppositeRole")
    }

    // New method for switching roles dynamically
    fun switchRoles() {
        val currentRole = _state.value.myRole
        val newRole = when (currentRole) {
            "camera" -> "mic"
            "mic" -> "camera"
            else -> return // Can't switch if no role is set
        }

        Log.d(TAG, "Switching roles from $currentRole to $newRole")

        // Send role switch command to remote device
        sendCommand(Command("role_switch", "$newRole:$currentRole"))

        // Update local state
        updateState {
            copy(
                myRole = newRole,
                remoteRole = currentRole,
                statusMessage = "Switched to: $newRole",
                // Reset recording state when switching roles
                isRecording = false,
                // Clear role-specific files when switching
                pendingAudioFile = if (newRole == "camera") null else pendingAudioFile,
                receivedAudioFile = if (newRole == "mic") null else receivedAudioFile,
                recordedVideoFile = if (newRole == "mic") null else recordedVideoFile
            )
        }
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
                    remoteRole = "",
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
            "role_assignment" -> {
                // Handle initial role assignment from remote device
                val roles = command.data.split(":")
                if (roles.size == 2) {
                    val remoteRole = roles[0]
                    val myNewRole = roles[1]

                    Log.d(TAG, "Received role assignment - Remote: $remoteRole, My new role: $myNewRole")
                    updateState {
                        copy(
                            myRole = myNewRole,
                            remoteRole = remoteRole,
                            statusMessage = "You are now: $myNewRole",
                            lastError = null
                        )
                    }
                }
            }

            "role_switch" -> {
                // Handle role switching from remote device
                val roles = command.data.split(":")
                if (roles.size == 2) {
                    val remoteNewRole = roles[0]
                    val myNewRole = roles[1]

                    Log.d(TAG, "Received role switch - Remote switched to: $remoteNewRole, I am now: $myNewRole")
                    updateState {
                        copy(
                            myRole = myNewRole,
                            remoteRole = remoteNewRole,
                            statusMessage = "Roles switched! You are now: $myNewRole",
                            // Reset recording state when roles switch
                            isRecording = false,
                            // Clear role-specific files when switching
                            pendingAudioFile = if (myNewRole == "camera") null else pendingAudioFile,
                            receivedAudioFile = if (myNewRole == "mic") null else receivedAudioFile,
                            recordedVideoFile = if (myNewRole == "mic") null else recordedVideoFile,
                            lastError = null
                        )
                    }
                }
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
                remoteRole = "",
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