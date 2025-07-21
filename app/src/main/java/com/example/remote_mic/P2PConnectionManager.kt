package com.example.remote_mic

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.charset.StandardCharsets

enum class DeviceRole {
    CAMERA, MICROPHONE, UNKNOWN
}

enum class ConnectionState {
    IDLE, HOSTING, SEARCHING, CONNECTING, CONNECTED, ROLE_SELECTION
}

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val role: DeviceRole = DeviceRole.UNKNOWN
)

@Serializable
data class Message(
    val type: String,
    val role: DeviceRole? = null,
    val command: String? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val isHost: Boolean = false,
    val localRole: DeviceRole = DeviceRole.UNKNOWN,
    val connectedDevice: DeviceInfo? = null,
    val discoveredDevices: List<DeviceInfo> = emptyList(),
    val statusMessage: String = "",
    val showInstructions: Boolean = false,
    val recordingState: String = "idle", // idle, recording, paused
    val receivedAudioFile: File? = null
)

class P2PConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "P2PConnectionManager"
        private const val SERVICE_ID = "com.example.remote_mic"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var connectedEndpointId: String? = null
    private val discoveredDevicesMap = mutableMapOf<String, DeviceInfo>()

    // File transfer manager
    private val fileTransferManager = FileTransferManager(
        context = context,
        sendPayload = { endpointId, payload ->
            Log.d(TAG, "Sending payload of type: ${payload.type}")
            connectionsClient.sendPayload(endpointId, payload)
        },
        onFileReceived = { file ->
            Log.d(TAG, "File received callback triggered: ${file.name}")
            handleReceivedAudioFile(file)
        }
    )

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: ${connectionInfo.endpointName}")
            updateStatus("Connecting to ${connectionInfo.endpointName}...")

            // Auto-accept connection
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    connectedEndpointId = endpointId
                    updateState { copy(
                        connectionState = ConnectionState.ROLE_SELECTION,
                        connectedDevice = DeviceInfo(endpointId, "Connected Device"),
                        statusMessage = "Connection established! Choose your role."
                    )}
                    stopAdvertising()
                    stopDiscovery()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected")
                    updateStatus("Connection was rejected")
                }
                else -> {
                    Log.d(TAG, "Connection failed")
                    updateStatus("Connection failed")
                    resetToIdle()
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            resetToIdle()
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Device found: ${info.endpointName}")
            val device = DeviceInfo(endpointId, info.endpointName)
            discoveredDevicesMap[endpointId] = device
            updateDiscoveredDevices()
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Device lost: $endpointId")
            discoveredDevicesMap.remove(endpointId)
            updateDiscoveredDevices()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Payload received from $endpointId, type: ${payload.type}")

            when (payload.type) {
                Payload.Type.BYTES -> {
                    payload.asBytes()?.let { bytes ->
                        try {
                            val messageString = String(bytes, StandardCharsets.UTF_8)
                            Log.d(TAG, "Received message: $messageString")

                            // Check if it's a JSON message or file metadata
                            if (messageString.trim().startsWith("{") && messageString.contains("\"type\"")) {
                                // It's a control message
                                val message = Json.decodeFromString<Message>(messageString)
                                handleMessage(message)
                            } else if (messageString.contains("fileName")) {
                                // It's file metadata
                                Log.d(TAG, "Received file metadata, passing to FileTransferManager")
                                fileTransferManager.receiveFile(payload)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing bytes payload", e)
                            // Try to handle as file metadata anyway
                            fileTransferManager.receiveFile(payload)
                        }
                    }
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "Received file payload, passing to FileTransferManager")
                    fileTransferManager.receiveFile(payload)
                }
                else -> {
                    Log.d(TAG, "Received unsupported payload type: ${payload.type}")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            Log.d(TAG, "Transfer update for payload ${update.payloadId}: status=${update.status}, progress=${update.bytesTransferred}/${update.totalBytes}")

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    if (update.totalBytes > 0) {
                        val progress = update.bytesTransferred.toFloat() / update.totalBytes.toFloat()
                        fileTransferManager.updateTransferProgress(update.payloadId, progress)
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "Transfer successful for payload: ${update.payloadId}")
                    fileTransferManager.onTransferComplete(update.payloadId, true)
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.e(TAG, "Transfer failed for payload: ${update.payloadId}")
                    fileTransferManager.onTransferComplete(update.payloadId, false)
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    Log.d(TAG, "Transfer canceled for payload: ${update.payloadId}")
                    fileTransferManager.onTransferComplete(update.payloadId, false)
                }
            }
        }
    }

    private fun handleMessage(message: Message) {
        Log.d(TAG, "Handling message: ${message.type}")
        when (message.type) {
            "role_selected" -> {
                message.role?.let { remoteRole ->
                    Log.d(TAG, "Remote device selected role: $remoteRole")
                    updateState { copy(
                        connectedDevice = connectedDevice?.copy(role = remoteRole),
                        connectionState = ConnectionState.CONNECTED,
                        statusMessage = "Remote device is ${remoteRole.name}"
                    )}
                }
            }
            "recording_command" -> {
                message.command?.let { command ->
                    Log.d(TAG, "Recording command received: $command")
                    updateState { copy(recordingState = when(command) {
                        "start_recording" -> "recording"
                        "pause_recording" -> "paused"
                        "resume_recording" -> "recording"
                        "stop_recording" -> "idle"
                        else -> recordingState
                    })}
                }
            }
            "audio_file_ready" -> {
                message.filePath?.let { filePath ->
                    Log.d(TAG, "Audio file ready notification: $filePath")
                }
            }
        }
    }

    private fun handleReceivedAudioFile(file: File) {
        Log.d(TAG, "Handling received audio file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()}")
        updateState { copy(receivedAudioFile = file) }
    }

    fun createConnection() {
        updateState { copy(
            connectionState = ConnectionState.HOSTING,
            isHost = true,
            statusMessage = "Creating connection...",
            showInstructions = true
        )}

        val deviceName = "Remote_Mic_${android.os.Build.MODEL}"
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startAdvertising(
            deviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            updateStatus("Waiting for other device to join...")
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to start advertising", exception)
            updateStatus("Failed to create connection")
            resetToIdle()
        }
    }

    fun joinConnection() {
        updateState { copy(
            connectionState = ConnectionState.SEARCHING,
            isHost = false,
            statusMessage = "Searching for devices...",
            discoveredDevices = emptyList()
        )}

        discoveredDevicesMap.clear()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            updateStatus("Looking for nearby devices...")
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to start discovery", exception)
            updateStatus("Failed to search for devices")
            resetToIdle()
        }
    }

    fun connectToDevice(deviceId: String) {
        val device = discoveredDevicesMap[deviceId] ?: return
        updateState { copy(connectionState = ConnectionState.CONNECTING) }
        updateStatus("Connecting to ${device.name}...")

        connectionsClient.requestConnection(
            "Remote_Mic_${android.os.Build.MODEL}",
            deviceId,
            connectionLifecycleCallback
        ).addOnFailureListener { exception ->
            Log.e(TAG, "Failed to connect", exception)
            updateStatus("Failed to connect to device")
            updateState { copy(connectionState = ConnectionState.SEARCHING) }
        }
    }

    fun selectRole(role: DeviceRole) {
        Log.d(TAG, "Local role selected: $role")
        updateState { copy(
            localRole = role,
            connectionState = ConnectionState.CONNECTED,
            statusMessage = "You are the ${role.name}"
        )}

        // Send role to connected device
        connectedEndpointId?.let { endpointId ->
            sendMessage(endpointId, Message("role_selected", role))
        }
    }

    fun sendRecordingCommand(command: String, filePath: String? = null) {
        Log.d(TAG, "Sending recording command: $command")
        connectedEndpointId?.let { endpointId ->
            sendMessage(endpointId, Message("recording_command", command = command, filePath = filePath))
        }
    }

    suspend fun sendAudioFile(audioFile: File) {
        Log.d(TAG, "Sending audio file: ${audioFile.name}, size: ${audioFile.length()}")
        connectedEndpointId?.let { endpointId ->
            try {
                // First notify that audio file is ready
                sendMessage(endpointId, Message("audio_file_ready", filePath = audioFile.absolutePath))

                // Then send the actual file
                fileTransferManager.sendFile(endpointId, audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send audio file", e)
            }
        } ?: run {
            Log.e(TAG, "No connected endpoint to send file to")
        }
    }

    private fun sendMessage(endpointId: String, message: Message) {
        try {
            val json = Json.encodeToString(message)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            Log.d(TAG, "Sending message: $json")
            connectionsClient.sendPayload(endpointId, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    fun disconnect() {
        connectionsClient.stopAllEndpoints()
        fileTransferManager.cleanup()
        resetToIdle()
    }

    fun hideInstructions() {
        updateState { copy(showInstructions = false) }
    }

    fun clearReceivedAudioFile() {
        Log.d(TAG, "Clearing received audio file")
        updateState { copy(receivedAudioFile = null) }
    }

    private fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    private fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    private fun resetToIdle() {
        connectedEndpointId = null
        discoveredDevicesMap.clear()
        updateState { copy(
            connectionState = ConnectionState.IDLE,
            isHost = false,
            localRole = DeviceRole.UNKNOWN,
            connectedDevice = null,
            discoveredDevices = emptyList(),
            statusMessage = "",
            showInstructions = false,
            recordingState = "idle",
            receivedAudioFile = null
        )}
    }

    private fun updateState(update: AppState.() -> AppState) {
        _state.value = _state.value.update()
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, "Status update: $message")
        updateState { copy(statusMessage = message) }
    }

    private fun updateDiscoveredDevices() {
        updateState { copy(discoveredDevices = discoveredDevicesMap.values.toList()) }
    }
}