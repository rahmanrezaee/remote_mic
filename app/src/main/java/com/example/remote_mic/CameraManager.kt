// Add this to your CameraManager.kt file to make it compatible with the screen

package com.example.remote_mic

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Make sure CameraState is properly defined
data class CameraState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDuration: Long = 0L,
    val isFlashOn: Boolean = false,
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    val outputFile: File? = null,
    val errorMessage: String? = null,
    val isProcessing: Boolean = false
)

// Create a proper interface for CameraManager
interface ICameraManager {
    val state: StateFlow<CameraState>
    suspend fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner)
    fun startRecording()
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording()
    fun toggleFlash()
    fun switchCamera()
    fun release()
}

class CameraManager(
    private val context: Context,
    private val onRecordingCommand: (String, String?) -> Unit // command, filePath
) : ICameraManager {
    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var recordingStartTime: Long = 0L

    private val videosDir by lazy {
        File(context.getExternalFilesDir(null), "RemoteMicVideos").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Videos directory created: $created, path: $absolutePath")
            }
        }
    }

    override suspend fun initializeCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        try {
            Log.d(TAG, "Initializing camera...")
            cameraProvider = ProcessCameraProvider.getInstance(context).get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    _state.value.cameraSelector,
                    preview,
                    videoCapture
                )

                Log.d(TAG, "Camera initialized successfully")
                updateState { copy(errorMessage = null) }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                updateState { copy(errorMessage = "Camera binding failed: ${exc.message}") }
            }

        } catch (exc: Exception) {
            Log.e(TAG, "Camera initialization failed", exc)
            updateState { copy(errorMessage = "Camera initialization failed: ${exc.message}") }
        }
    }

    override fun startRecording() {
        Log.d(TAG, "startRecording() called")

        if (_state.value.isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        val videoCapture = this.videoCapture ?: run {
            Log.e(TAG, "VideoCapture is null")
            updateState { copy(errorMessage = "Camera not initialized") }
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        // Create file in app's external files directory
        val videoFile = File(videosDir, "RemoteMic_Video_$name.mp4")
        Log.d(TAG, "Creating video file: ${videoFile.absolutePath}")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply {
                // Enable audio recording if permission is granted
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                    Log.d(TAG, "Audio enabled for video recording")
                } else {
                    Log.w(TAG, "Audio permission not granted")
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                handleRecordEvent(recordEvent, videoFile)
            }
    }

    private fun handleRecordEvent(recordEvent: VideoRecordEvent, videoFile: File) {
        when (recordEvent) {
            is VideoRecordEvent.Start -> {
                recordingStartTime = System.currentTimeMillis()
                updateState {
                    copy(
                        isRecording = true,
                        isPaused = false,
                        errorMessage = null,
                        outputFile = videoFile,
                        isProcessing = false,
                        recordingDuration = 0L
                    )
                }
                onRecordingCommand("start_recording", videoFile.absolutePath)
                Log.d(TAG, "Video recording started: ${videoFile.absolutePath}")
            }
            is VideoRecordEvent.Finalize -> {
                updateState { copy(isProcessing = false) }

                if (!recordEvent.hasError()) {
                    Log.d(TAG, "Video recording completed successfully")
                    Log.d(TAG, "File size: ${videoFile.length()} bytes")
                    Log.d(TAG, "File exists: ${videoFile.exists()}")

                    updateState {
                        copy(
                            isRecording = false,
                            isPaused = false,
                            outputFile = videoFile,
                            recordingDuration = 0L
                        )
                    }
                    onRecordingCommand("stop_recording", videoFile.absolutePath)
                } else {
                    recording?.close()
                    recording = null
                    Log.e(TAG, "Recording error: ${recordEvent.error}")
                    updateState {
                        copy(
                            isRecording = false,
                            isPaused = false,
                            errorMessage = "Recording error: ${recordEvent.error}",
                            outputFile = null,
                            recordingDuration = 0L
                        )
                    }
                    onRecordingCommand("error", recordEvent.error.toString())
                }
            }
            is VideoRecordEvent.Pause -> {
                updateState { copy(isPaused = true) }
                onRecordingCommand("pause_recording", null)
                Log.d(TAG, "Video recording paused")
            }
            is VideoRecordEvent.Resume -> {
                updateState { copy(isPaused = false) }
                onRecordingCommand("resume_recording", null)
                Log.d(TAG, "Video recording resumed")
            }
            is VideoRecordEvent.Status -> {
                // Update recording duration
                val currentDuration = System.currentTimeMillis() - recordingStartTime
                updateState {
                    copy(recordingDuration = currentDuration)
                }
            }
        }
    }

    override fun stopRecording() {
        Log.d(TAG, "stopRecording() called")

        if (recording != null) {
            updateState { copy(isProcessing = true) }
            recording?.stop()
            recording = null
            Log.d(TAG, "Stop recording requested")
        } else {
            Log.w(TAG, "No active recording to stop")
        }
    }

    override fun pauseRecording() {
        Log.d(TAG, "pauseRecording() called")

        if (_state.value.isRecording && !_state.value.isPaused) {
            recording?.pause()
            Log.d(TAG, "Pause recording requested")
        } else {
            Log.w(TAG, "Cannot pause - not recording or already paused")
        }
    }

    override fun resumeRecording() {
        Log.d(TAG, "resumeRecording() called")

        if (_state.value.isRecording && _state.value.isPaused) {
            recording?.resume()
            Log.d(TAG, "Resume recording requested")
        } else {
            Log.w(TAG, "Cannot resume - not recording or not paused")
        }
    }

    override fun toggleFlash() {
        val camera = this.camera ?: return
        val newFlashState = !_state.value.isFlashOn

        try {
            camera.cameraControl.enableTorch(newFlashState)
            updateState { copy(isFlashOn = newFlashState) }
            Log.d(TAG, "Flash toggled: $newFlashState")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flash", e)
            updateState { copy(errorMessage = "Flash error: ${e.message}") }
        }
    }

    override fun switchCamera() {
        val newSelector = if (_state.value.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        updateState { copy(cameraSelector = newSelector) }
        Log.d(TAG, "Camera switched to: ${if (newSelector == CameraSelector.DEFAULT_BACK_CAMERA) "BACK" else "FRONT"}")

        // Rebind camera with new selector
        try {
            cameraProvider?.unbindAll()
            // Note: The UI should call initializeCamera again after switching
            updateState { copy(errorMessage = "Camera switched - please wait for reinitialization") }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching camera", e)
            updateState { copy(errorMessage = "Camera switch error: ${e.message}") }
        }
    }

    private fun updateState(update: CameraState.() -> CameraState) {
        _state.value = _state.value.update()
    }

    override fun release() {
        Log.d(TAG, "Releasing camera resources")
        try {
            recording?.stop()
            recording = null
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
    }
}