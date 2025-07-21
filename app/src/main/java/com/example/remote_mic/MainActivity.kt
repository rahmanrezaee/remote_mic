package com.example.remote_mic

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.remote_mic.ui.theme.RemoteMicTheme
import com.example.remote_mic.screens.*
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var connectionManager: P2PConnectionManager
    private lateinit var cameraManager: CameraManager
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "Creating MainActivity")

        // Initialize directories first
        initializeDirectories()

        permissionsManager = PermissionsManager(this)
        connectionManager = P2PConnectionManager(this)

        // Initialize camera manager with recording command callback
        cameraManager = CameraManager(this) { command, filePath ->
            Log.d("MainActivity", "Camera command: $command, filePath: $filePath")
            connectionManager.sendRecordingCommand(command, filePath)
        }

        // Initialize audio manager with file ready callback
        audioManager = AudioManager(this) { audioFile ->
            Log.d("MainActivity", "Audio file ready: ${audioFile.name}, size: ${audioFile.length()}")
            // Send audio file to camera device
            lifecycleScope.launch {
                connectionManager.sendAudioFile(audioFile)
            }
        }

        permissionsManager.initialize()

        enableEdgeToEdge()
        setContent {
            RemoteMicTheme {
                RemoteMicApp(
                    permissionsManager = permissionsManager,
                    connectionManager = connectionManager,
                    cameraManager = cameraManager,
                    audioManager = audioManager
                )
            }
        }
    }

    private fun initializeDirectories() {
        try {
            val videosDir = File(getExternalFilesDir(null), "RemoteMicVideos")
            val audiosDir = File(getExternalFilesDir(null), "RemoteMicAudios")
            val receivedDir = File(getExternalFilesDir(null), "ReceivedFiles")

            videosDir.mkdirs()
            audiosDir.mkdirs()
            receivedDir.mkdirs()

            Log.d("MainActivity", "Directories initialized:")
            Log.d("MainActivity", "Videos: ${videosDir.absolutePath} (exists: ${videosDir.exists()})")
            Log.d("MainActivity", "Audios: ${audiosDir.absolutePath} (exists: ${audiosDir.exists()})")
            Log.d("MainActivity", "Received: ${receivedDir.absolutePath} (exists: ${receivedDir.exists()})")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize directories", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "Destroying MainActivity")
        try {
            cameraManager.release()
            audioManager.cleanup()
            connectionManager.disconnect()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMicApp(
    permissionsManager: PermissionsManager,
    connectionManager: P2PConnectionManager,
    cameraManager: CameraManager,
    audioManager: AudioManager
) {
    val permissionState by permissionsManager.permissionState.collectAsStateWithLifecycle()
    val appState by connectionManager.state.collectAsStateWithLifecycle()

    // Log state changes for debugging
    LaunchedEffect(appState.connectionState, appState.localRole, appState.recordingState) {
        Log.d("RemoteMicApp", "State changed:")
        Log.d("RemoteMicApp", "  Connection: ${appState.connectionState}")
        Log.d("RemoteMicApp", "  Local Role: ${appState.localRole}")
        Log.d("RemoteMicApp", "  Recording: ${appState.recordingState}")
        Log.d("RemoteMicApp", "  Connected Device: ${appState.connectedDevice?.name}")
        Log.d("RemoteMicApp", "  Received Audio File: ${appState.receivedAudioFile?.name}")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            !permissionState.allGranted -> {
                Log.d("RemoteMicApp", "Showing permission screen")
                PermissionScreen(
                    permissionState = permissionState,
                    onRequestPermissions = {
                        Log.d("RemoteMicApp", "Requesting permissions")
                        permissionsManager.requestPermissions()
                    }
                )
            }
            appState.connectionState == ConnectionState.IDLE -> {
                Log.d("RemoteMicApp", "Showing welcome screen")
                WelcomeScreen(
                    onCreateConnection = {
                        Log.d("RemoteMicApp", "Creating connection")
                        connectionManager.createConnection()
                    },
                    onJoinConnection = {
                        Log.d("RemoteMicApp", "Joining connection")
                        connectionManager.joinConnection()
                    }
                )
            }
            appState.connectionState == ConnectionState.HOSTING -> {
                Log.d("RemoteMicApp", "Showing hosting screen")
                HostingScreen(
                    appState = appState,
                    onDismissInstructions = { connectionManager.hideInstructions() },
                    onDisconnect = {
                        Log.d("RemoteMicApp", "Disconnecting from hosting")
                        connectionManager.disconnect()
                    }
                )
            }
            appState.connectionState == ConnectionState.SEARCHING -> {
                Log.d("RemoteMicApp", "Showing searching screen")
                SearchingScreen(
                    appState = appState,
                    onConnectToDevice = { deviceId ->
                        Log.d("RemoteMicApp", "Connecting to device: $deviceId")
                        connectionManager.connectToDevice(deviceId)
                    },
                    onBack = {
                        Log.d("RemoteMicApp", "Back from searching")
                        connectionManager.disconnect()
                    }
                )
            }
            appState.connectionState == ConnectionState.CONNECTING -> {
                Log.d("RemoteMicApp", "Showing connecting screen")
                ConnectingScreen(
                    appState = appState,
                    onBack = {
                        Log.d("RemoteMicApp", "Back from connecting")
                        connectionManager.disconnect()
                    }
                )
            }
            appState.connectionState == ConnectionState.ROLE_SELECTION -> {
                Log.d("RemoteMicApp", "Showing role selection screen")
                RoleSelectionScreen(
                    appState = appState,
                    onSelectRole = { role ->
                        Log.d("RemoteMicApp", "Role selected: $role")
                        connectionManager.selectRole(role)
                    },
                    onDisconnect = {
                        Log.d("RemoteMicApp", "Disconnecting from role selection")
                        connectionManager.disconnect()
                    }
                )
            }
            appState.connectionState == ConnectionState.CONNECTED -> {
                Log.d("RemoteMicApp", "Connected - showing role-specific screen")
                when (appState.localRole) {
                    DeviceRole.CAMERA -> {
                        Log.d("RemoteMicApp", "Showing camera screen")
                        CameraScreen(
                            appState = appState,
                            cameraManager = cameraManager,
                            onDisconnect = {
                                Log.d("RemoteMicApp", "Disconnecting from camera")
                                connectionManager.disconnect()
                            },
                            onClearAudioFile = {
                                Log.d("RemoteMicApp", "Clearing received audio file")
                                connectionManager.clearReceivedAudioFile()
                            }
                        )
                    }
                    DeviceRole.MICROPHONE -> {
                        Log.d("RemoteMicApp", "Showing microphone screen")
                        MicrophoneScreen(
                            appState = appState,
                            audioManager = audioManager,
                            onDisconnect = {
                                Log.d("RemoteMicApp", "Disconnecting from microphone")
                                connectionManager.disconnect()
                            }
                        )
                    }
                    else -> {
                        Log.d("RemoteMicApp", "Unknown role, showing role selection again")
                        RoleSelectionScreen(
                            appState = appState,
                            onSelectRole = { role ->
                                Log.d("RemoteMicApp", "Role selected (retry): $role")
                                connectionManager.selectRole(role)
                            },
                            onDisconnect = {
                                Log.d("RemoteMicApp", "Disconnecting from role selection (retry)")
                                connectionManager.disconnect()
                            }
                        )
                    }
                }
            }
        }
    }
}