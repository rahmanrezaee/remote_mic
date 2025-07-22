package com.example.remote_mic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import com.example.remote_mic.ui.RemoteMicApp
import com.example.remote_mic.managers.AudioManager
import com.example.remote_mic.managers.CameraManager
import com.example.remote_mic.managers.ConnectionManager

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