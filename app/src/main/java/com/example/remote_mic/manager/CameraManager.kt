package com.example.remote_mic.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class CameraManager(private val context: Context) {
    private val TAG = "CameraManager"
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    private var lastVideoFile: File? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    // Internal camera state
    private var isBackCamera = true
    private var currentFlashMode = ImageCapture.FLASH_MODE_OFF
    private var currentQuality = Quality.HD
    private var recordingStartTime = 0L
    private var timerJob: Job? = null

    // Callbacks
    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onVideoFileReady: ((File) -> Unit)? = null
    var onCameraStatusChanged: ((String) -> Unit)? = null
    var onRecordingTimeChanged: ((String) -> Unit)? = null

    private val videoDir by lazy {
        File(context.getExternalFilesDir(null), "video").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Video directory created: $created, path: $absolutePath")
            }
        }
    }

    // Public getters for UI state
    fun getLastVideoFile(): File? = lastVideoFile
    fun isBackCamera(): Boolean = isBackCamera
    fun getCurrentFlashMode(): Int = currentFlashMode
    fun getCurrentQuality(): Quality = currentQuality
    fun isCurrentlyRecording(): Boolean = isRecording

    fun getRecordingDuration(): String {
        return if (isRecording && recordingStartTime > 0) {
            val duration = System.currentTimeMillis() - recordingStartTime
            formatDuration(duration)
        } else "00:00"
    }

    fun getFlashModeIcon(): String {
        return when (currentFlashMode) {
            ImageCapture.FLASH_MODE_ON -> "flash_on"
            ImageCapture.FLASH_MODE_AUTO -> "flash_auto"
            else -> "flash_off"
        }
    }

    fun getQualityText(): String {
        return when (currentQuality) {
            Quality.UHD -> "4K"
            Quality.FHD -> "1080p FHD"
            Quality.HD -> "1080p"
            Quality.SD -> "720p"
            else -> "Auto"
        }
    }

    fun getCameraStatus(): String {
        return when {
            isRecording -> "🎥 Recording... ${getRecordingDuration()}"
            lastVideoFile?.exists() == true -> "✅ Ready (${lastVideoFile?.name})"
            else -> "⏹ Ready to record"
        }
    }

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        Log.d(TAG, "Initializing camera...")
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                setupCamera()
                Log.d(TAG, "Camera initialized successfully")
                onCameraStatusChanged?.invoke("Camera ready")
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                onCameraStatusChanged?.invoke("Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCamera() {
        try {
            val cameraProvider = this.cameraProvider ?: return
            val lifecycleOwner = this.lifecycleOwner ?: return
            val previewView = this.previewView ?: return

            // Build preview
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Build recorder with current quality
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(currentQuality))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            // Select camera
            val cameraSelector = if (isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                // Apply flash settings
                applyFlashSettings()

                onCameraStatusChanged?.invoke("Camera ready")

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                onCameraStatusChanged?.invoke("Camera binding failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Camera setup failed", e)
            onCameraStatusChanged?.invoke("Camera setup failed")
        }
    }

    private fun applyFlashSettings() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                val torchEnabled = currentFlashMode == ImageCapture.FLASH_MODE_ON
                cam.cameraControl.enableTorch(torchEnabled)
            }
        }
    }

    fun switchCamera() {
        Log.d(TAG, "Switching camera...")

        if (isRecording) {
            Log.w(TAG, "Cannot switch camera while recording")
            onCameraStatusChanged?.invoke("Cannot switch camera while recording")
            return
        }

        isBackCamera = !isBackCamera
        setupCamera()

        val cameraType = if (isBackCamera) "Back" else "Front"
        onCameraStatusChanged?.invoke("Switched to $cameraType camera")
        Log.d(TAG, "Camera switched to: $cameraType")
    }

    fun toggleFlash() {
        Log.d(TAG, "Toggling flash...")

        val camera = this.camera
        if (camera == null) {
            Log.w(TAG, "Camera not initialized")
            onCameraStatusChanged?.invoke("Camera not ready")
            return
        }

        if (!camera.cameraInfo.hasFlashUnit()) {
            Log.w(TAG, "Flash not available on this camera")
            onCameraStatusChanged?.invoke("Flash not available")
            return
        }

        currentFlashMode = when (currentFlashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_OFF
        }

        applyFlashSettings()

        val flashStatus = when (currentFlashMode) {
            ImageCapture.FLASH_MODE_OFF -> "Flash Off"
            ImageCapture.FLASH_MODE_ON -> "Flash On"
            ImageCapture.FLASH_MODE_AUTO -> "Flash Auto"
            else -> "Flash Off"
        }

        onCameraStatusChanged?.invoke(flashStatus)
        Log.d(TAG, "Flash mode changed to: $flashStatus")
    }

    fun setVideoQuality(quality: Quality) {
        Log.d(TAG, "Setting video quality to: $quality")

        if (isRecording) {
            Log.w(TAG, "Cannot change quality while recording")
            onCameraStatusChanged?.invoke("Cannot change quality while recording")
            return
        }

        currentQuality = quality
        setupCamera() // Reinitialize with new quality

        val qualityString = getQualityText()
        onCameraStatusChanged?.invoke("Quality: $qualityString")
        Log.d(TAG, "Video quality set to: $qualityString")
    }

    fun startRecording() {
        val videoCapture = this.videoCapture
        if (videoCapture == null) {
            Log.e(TAG, "VideoCapture not initialized")
            onCameraStatusChanged?.invoke("Camera not ready")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        Log.d(TAG, "Starting video recording...")

        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val videoFile = File(videoDir, "VID_${dateFormat.format(Date(timestamp))}.mp4")
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
                            recordingStartTime = System.currentTimeMillis()
                            Log.d(TAG, "Video recording started: ${videoFile.name}")
                            onRecordingStateChanged?.invoke(true)
                            onCameraStatusChanged?.invoke("Recording...")
                            startRecordingTimer()
                        }

                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            recordingStartTime = 0L
                            stopRecordingTimer()

                            if (!recordEvent.hasError()) {
                                Log.d(
                                    TAG,
                                    "Video recording finished successfully: ${videoFile.name}, size: ${videoFile.length()} bytes"
                                )
                                lastVideoFile = videoFile
                                onVideoFileReady?.invoke(videoFile)
                                onCameraStatusChanged?.invoke("Video saved: ${videoFile.name}")
                            } else {
                                Log.e(
                                    TAG,
                                    "Video recording finished with error: ${recordEvent.error}"
                                )
                                onCameraStatusChanged?.invoke("Recording failed")
                            }
                            onRecordingStateChanged?.invoke(false)
                            onRecordingTimeChanged?.invoke("00:00")
                        }

                        is VideoRecordEvent.Status -> {
                            // Handle status updates
                            Log.v(TAG, "Video recording status update")
                        }
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            isRecording = false
            recordingStartTime = 0L
            onRecordingStateChanged?.invoke(false)
            onCameraStatusChanged?.invoke("Recording failed")
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

    fun pauseRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, cannot pause")
            return
        }

        recording?.pause()
        stopRecordingTimer()
        Log.d(TAG, "Recording paused")
        onCameraStatusChanged?.invoke("Recording paused")
    }

    fun resumeRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, cannot resume")
            return
        }

        recording?.resume()
        startRecordingTimer()
        Log.d(TAG, "Recording resumed")
        onCameraStatusChanged?.invoke("Recording...")
    }

    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isRecording) {
                onRecordingTimeChanged?.invoke(getRecordingDuration())
                delay(1000)
            }
        }
    }

    private fun stopRecordingTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun hasFlashUnit(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    fun canSwitchCamera(): Boolean {
        val cameraProvider = this.cameraProvider ?: return false
        return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    fun getStatus(): String {
        return getCameraStatus()
    }

    fun release() {
        Log.d(TAG, "Releasing camera resources...")

        if (isRecording) {
            stopRecording()
        }

        stopRecordingTimer()
        cameraProvider?.unbindAll()
        camera = null
        preview = null
        videoCapture = null
        recording = null
        cameraProvider = null
        lifecycleOwner = null
        previewView = null

        Log.d(TAG, "Camera resources released")
    }
}