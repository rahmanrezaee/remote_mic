package com.example.remote_mic

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class AudioState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDuration: Long = 0L,
    val audioLevel: Float = 0f,
    val outputFile: File? = null,
    val errorMessage: String? = null,
    val isProcessingFile: Boolean = false
)

class AudioManager(
    private val context: Context,
    private val onAudioReady: (File) -> Unit
) {
    companion object {
        private const val TAG = "AudioManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val AUDIO_LEVEL_UPDATE_INTERVAL = 100L
    }

    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var audioLevelJob: Job? = null
    private var startTime: Long = 0L
    private var pausedDuration: Long = 0L

    private val audiosDir by lazy {
        File(context.getExternalFilesDir(null), "RemoteMicAudios").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "Audio directory created: $created, path: $absolutePath")
            }
        }
    }

    fun startRecording() {
        Log.d(TAG, "startRecording() called")

        if (_state.value.isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        try {
            val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())
            val audioFile = File(audiosDir, "RemoteMic_Audio_$timestamp.m4a")

            Log.d(TAG, "Creating audio file: ${audioFile.absolutePath}")

            // Create and configure MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)

                try {
                    prepare()
                    start()

                    startTime = System.currentTimeMillis()
                    pausedDuration = 0L

                    updateState {
                        copy(
                            isRecording = true,
                            isPaused = false,
                            outputFile = audioFile,
                            errorMessage = null,
                            isProcessingFile = false
                        )
                    }

                    startDurationTimer()
                    startAudioLevelMonitoring()

                    Log.d(TAG, "Audio recording started successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MediaRecorder", e)
                    updateState { copy(errorMessage = "Failed to start recording: ${e.message}") }
                    cleanup()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaRecorder", e)
            updateState { copy(errorMessage = "Setup error: ${e.message}") }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "stopRecording() called")

        if (!_state.value.isRecording) {
            Log.w(TAG, "Not recording, ignoring stop request")
            return
        }

        try {
            // Cancel ongoing jobs
            recordingJob?.cancel()
            audioLevelJob?.cancel()

            val outputFile = _state.value.outputFile

            Log.d(TAG, "Stopping MediaRecorder...")

            // Show processing state immediately
            updateState {
                copy(
                    isProcessingFile = true,
                    audioLevel = 0f
                )
            }

            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                Log.d(TAG, "MediaRecorder stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaRecorder", e)
                // Continue anyway to try to send the file
            }

            mediaRecorder = null

            // Update recording state
            updateState {
                copy(
                    isRecording = false,
                    isPaused = false,
                    recordingDuration = 0L
                )
            }

            // Check and send the file
            outputFile?.let { file ->
                Log.d(TAG, "Checking output file: ${file.absolutePath}")
                Log.d(TAG, "File exists: ${file.exists()}, size: ${file.length()}")

                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Audio file is valid, sending to camera device")

                    // Use a coroutine to handle file sending
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            // Small delay to ensure file is fully written
                            delay(500)

                            // Double-check file after delay
                            if (file.exists() && file.length() > 0) {
                                Log.d(TAG, "Calling onAudioReady callback")
                                onAudioReady(file)
                            } else {
                                Log.e(TAG, "File became invalid after delay")
                                updateState {
                                    copy(
                                        errorMessage = "Audio file became invalid",
                                        isProcessingFile = false
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in file sending coroutine", e)
                            updateState {
                                copy(
                                    errorMessage = "Failed to send audio: ${e.message}",
                                    isProcessingFile = false
                                )
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Audio file is invalid or empty")
                    updateState {
                        copy(
                            errorMessage = "Recording failed - file is empty or missing",
                            isProcessingFile = false
                        )
                    }
                }
            } ?: run {
                Log.e(TAG, "No output file to send")
                updateState {
                    copy(
                        errorMessage = "No output file found",
                        isProcessingFile = false
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecording", e)
            updateState {
                copy(
                    errorMessage = "Stop error: ${e.message}",
                    isProcessingFile = false,
                    isRecording = false,
                    isPaused = false
                )
            }
            cleanup()
        }
    }

    private fun pauseRecording() {
        Log.d(TAG, "pauseRecording() called")

        if (_state.value.isRecording && !_state.value.isPaused) {
            try {
                mediaRecorder?.pause()
                recordingJob?.cancel()
                audioLevelJob?.cancel()

                pausedDuration += System.currentTimeMillis() - startTime

                updateState { copy(isPaused = true, audioLevel = 0f) }
                Log.d(TAG, "Audio recording paused")
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing recording", e)
                updateState { copy(errorMessage = "Pause error: ${e.message}") }
            }
        }
    }

    private fun resumeRecording() {
        Log.d(TAG, "resumeRecording() called")

        if (_state.value.isRecording && _state.value.isPaused) {
            try {
                mediaRecorder?.resume()
                startTime = System.currentTimeMillis()

                updateState { copy(isPaused = false) }
                startDurationTimer()
                startAudioLevelMonitoring()

                Log.d(TAG, "Audio recording resumed")
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming recording", e)
                updateState { copy(errorMessage = "Resume error: ${e.message}") }
            }
        }
    }

    private fun startDurationTimer() {
        recordingJob = CoroutineScope(Dispatchers.Main).launch {
            while (_state.value.isRecording && !_state.value.isPaused) {
                val currentDuration = System.currentTimeMillis() - startTime + pausedDuration
                updateState { copy(recordingDuration = currentDuration) }
                delay(1000) // Update every second
            }
        }
    }

    private fun startAudioLevelMonitoring() {
        audioLevelJob = CoroutineScope(Dispatchers.Main).launch {
            while (_state.value.isRecording && !_state.value.isPaused) {
                try {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    val level = if (amplitude > 0) {
                        (20 * kotlin.math.log10(amplitude.toDouble() / 32767.0)).toFloat()
                            .coerceIn(-60f, 0f) // dB range
                            .let { (it + 60f) / 60f } // Normalize to 0-1
                    } else {
                        0f
                    }

                    updateState { copy(audioLevel = level) }
                    delay(AUDIO_LEVEL_UPDATE_INTERVAL)

                } catch (e: Exception) {
                    // Ignore amplitude reading errors during recording
                    delay(AUDIO_LEVEL_UPDATE_INTERVAL)
                }
            }
        }
    }

    fun handleRemoteCommand(command: String) {
        Log.d(TAG, "Handling remote command: $command (current state: recording=${_state.value.isRecording}, paused=${_state.value.isPaused})")

        when (command) {
            "start_recording" -> {
                if (!_state.value.isRecording) {
                    startRecording()
                } else {
                    Log.d(TAG, "Already recording, ignoring start command")
                }
            }
            "stop_recording" -> {
                if (_state.value.isRecording) {
                    stopRecording()
                } else {
                    Log.d(TAG, "Not recording, ignoring stop command")
                }
            }
            "pause_recording" -> {
                if (_state.value.isRecording && !_state.value.isPaused) {
                    pauseRecording()
                } else {
                    Log.d(TAG, "Cannot pause (recording: ${_state.value.isRecording}, paused: ${_state.value.isPaused})")
                }
            }
            "resume_recording" -> {
                if (_state.value.isRecording && _state.value.isPaused) {
                    resumeRecording()
                } else {
                    Log.d(TAG, "Cannot resume (recording: ${_state.value.isRecording}, paused: ${_state.value.isPaused})")
                }
            }
            else -> {
                Log.w(TAG, "Unknown command: $command")
            }
        }
    }

    fun onAudioFileSent() {
        Log.d(TAG, "Audio file sent successfully")
        updateState { copy(isProcessingFile = false) }
    }

    private fun updateState(update: AudioState.() -> AudioState) {
        _state.value = _state.value.update()
    }

    private fun releaseResources() {
        try {
            recordingJob?.cancel()
            audioLevelJob?.cancel()
            mediaRecorder?.release()
            mediaRecorder = null
            Log.d(TAG, "Resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource release", e)
        }
    }

    fun cleanup() {
        Log.d(TAG, "cleanup() called")
        if (_state.value.isRecording) {
            stopRecording()
        }
        releaseResources()
        updateState {
            copy(
                isRecording = false,
                isPaused = false,
                recordingDuration = 0L,
                audioLevel = 0f,
                outputFile = null,
                errorMessage = null,
                isProcessingFile = false
            )
        }
    }
}