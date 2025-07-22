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