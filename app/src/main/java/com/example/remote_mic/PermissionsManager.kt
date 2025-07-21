package com.example.remote_mic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val allGranted: Boolean = false,
    val deniedPermissions: List<String> = emptyList(),
    val isRequesting: Boolean = false
)

class PermissionsManager(private val activity: ComponentActivity) {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private val requiredPermissions = buildList {
        // Location permissions (required for nearby connections)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // WiFi permissions
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)

        // Android 13+ nearby wifi devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Camera and Audio permissions
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    fun initialize() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }

        checkPermissions()
    }

    fun requestPermissions() {
        if (_permissionState.value.isRequesting) return

        val deniedPermissions = getDeniedPermissions()
        if (deniedPermissions.isNotEmpty()) {
            _permissionState.value = _permissionState.value.copy(isRequesting = true)
            permissionLauncher.launch(deniedPermissions.toTypedArray())
        }
    }

    private fun checkPermissions() {
        val deniedPermissions = getDeniedPermissions()
        val allGranted = deniedPermissions.isEmpty()

        _permissionState.value = PermissionState(
            allGranted = allGranted,
            deniedPermissions = deniedPermissions,
            isRequesting = false
        )
    }

    private fun getDeniedPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()
        val allGranted = deniedPermissions.isEmpty()

        _permissionState.value = PermissionState(
            allGranted = allGranted,
            deniedPermissions = deniedPermissions,
            isRequesting = false
        )
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionStatusText(): String {
        val state = _permissionState.value
        return when {
            state.isRequesting -> "Requesting permissions..."
            state.allGranted -> "All permissions granted"
            state.deniedPermissions.isNotEmpty() -> "Missing permissions: ${state.deniedPermissions.size}"
            else -> "Checking permissions..."
        }
    }

    fun hasCameraPermission(): Boolean = hasPermission(Manifest.permission.CAMERA)
    fun hasAudioPermission(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)
    fun hasLocationPermissions(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
}