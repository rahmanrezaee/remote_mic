package com.example.remote_mic.managers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioManager(private val context: Context) {
    private val TAG = "AudioManager"
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    var onRecordingTimeChanged: ((String) -> Unit)? = null
    private var recordingStartTime: Long = 0
    private var timerJob: Job? = null

    var onAudioFileReady: ((File) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    private val audioDir by lazy {
        File(context.getExternalFilesDir(null), "audio").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Audio directory created: $created, path: $absolutePath")
            }
        }
    }

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        Log.d(TAG, "Starting audio recording...")

        try {
            cleanup() // Ensure clean state

            val timestamp = System.currentTimeMillis()
            val audioFile = File(audioDir, "audio_${timestamp}.mp4")
            currentAudioFile = audioFile

            Log.d(TAG, "Recording to file: ${audioFile.absolutePath}")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000) // 64kbps for good quality/size balance
                setAudioSamplingRate(22050)   // 22kHz sampling rate
                setOutputFile(audioFile.absolutePath)

                setOnErrorListener { mr, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    val errorMsg = "Recording error: $what/$extra"
                    onRecordingError?.invoke(errorMsg)
                    cleanup()
                }

                setOnInfoListener { mr, what, extra ->
                    Log.d(TAG, "MediaRecorder info: what=$what, extra=$extra")
                }

                try {
                    prepare()
                    start()
                    recordingStartTime = System.currentTimeMillis()
                    startRecordingTimer()
                    isRecording = true


                    Log.d(TAG, "Audio recording started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MediaRecorder", e)
                    cleanup()
                    throw e
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            onRecordingError?.invoke("Failed to start recording: ${e.message}")
            releaseMediaRecorder()
        }
    }
    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(1000) // Update every second
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - recordingStartTime
                val formattedTime = formatTime(elapsedTime)
                onRecordingTimeChanged?.invoke(formattedTime)
            }
        }
    }

    private fun stopRecordingTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            return
        }
        stopRecordingTimer()
        onRecordingTimeChanged?.invoke("00:00")
        Log.d(TAG, "Stopping audio recording...")

        try {
            mediaRecorder?.apply {
                try {
                    stop()
                    Log.d(TAG, "MediaRecorder stopped successfully")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "MediaRecorder stop failed (likely short recording)", e)
                    // Don't throw here - file might still be valid
                }
                release()
            }
            mediaRecorder = null
            isRecording = false

            // Process the recorded file
            currentAudioFile?.let { file ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Wait for file system to sync
                        delay(500)

                        if (validateAudioFile(file)) {
                            Log.d(
                                TAG,
                                "Audio file validated successfully: ${file.name}, size: ${file.length()} bytes"
                            )

                            // Notify on main thread
                            withContext(Dispatchers.Main) {
                                onAudioFileReady?.invoke(file)
                            }
                        } else {
                            Log.e(TAG, "Audio file validation failed")
                            withContext(Dispatchers.Main) {
                                onRecordingError?.invoke("Recorded file is invalid or too small")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing audio file", e)
                        withContext(Dispatchers.Main) {
                            onRecordingError?.invoke("Error processing audio: ${e.message}")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onRecordingError?.invoke("Error stopping recording: ${e.message}")
            releaseMediaRecorder()
        }
    }

    private fun validateAudioFile(file: File): Boolean {
        return file.exists() &&
                file.length() > 1024 && // At least 1KB
                file.canRead() &&
                file.parentFile?.exists() == true
    }

    private fun releaseMediaRecorder() {
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
            isRecording -> "🎤 Recording audio..."
            currentAudioFile?.exists() == true -> "✅ Ready (${currentAudioFile?.name})"
            else -> "⏹ Ready to record"
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up audio manager...")
        releaseMediaRecorder()
    }
}