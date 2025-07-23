package com.example.remote_mic.managers

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File

class AudioVideoMerger(private val context: Context) {
    companion object {
        private const val TAG = "AudioVideoMerger"
    }

    data class MergeOptions(
        val audioVolume: Float = 1.0f,
        val videoVolume: Float = 0.5f, // Keep some original video audio
        val syncOffset: Float = 0.0f, // Audio sync offset in seconds
        val fadeIn: Float = 0.0f,
        val fadeOut: Float = 0.0f,
        val replaceAudio: Boolean = false // If true, completely replace video audio
    )

    fun mergeAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFileName: String = "merged_${System.currentTimeMillis()}.mp4",
        options: MergeOptions = MergeOptions(),
        onProgress: ((Int, String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🎬 Starting enhanced merge process...")

                // Validate files
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(5, "Validating files...")
                }

                if (!validateFiles(videoFile, audioFile)) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                // Create output directory
                val outputDir = File(context.getExternalFilesDir(null), "merged")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)

                // Delete existing output file
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                Log.d(TAG, "📁 Files ready for merge:")
                Log.d(TAG, "Video: ${videoFile.absolutePath} (${videoFile.length()} bytes)")
                Log.d(TAG, "Audio: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                Log.d(TAG, "Output: ${outputFile.absolutePath}")

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(15, "Preparing merge...")
                }

                // Get media information first
                val videoInfo = getVideoInfo(videoFile)
                val audioInfo = getAudioInfo(audioFile)

                Log.d(TAG, "Video info: $videoInfo")
                Log.d(TAG, "Audio info: $audioInfo")

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(25, "Processing media...")
                }

                // Perform merge on main thread for FFmpeg
                withContext(Dispatchers.Main) {
                    performEnhancedMerge(
                        videoFile,
                        audioFile,
                        outputFile,
                        options,
                        onProgress,
                        onComplete
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in merge process", e)
                withContext(Dispatchers.Main) { onComplete(null) }
            }
        }
    }

    private fun validateFiles(videoFile: File, audioFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "❌ Video file invalid")
            return false
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "❌ Audio file invalid")
            return false
        }

        // Check file extensions
        val videoExt = videoFile.extension.lowercase()
        val audioExt = audioFile.extension.lowercase()

        if (videoExt !in listOf("mp4", "mov", "avi", "mkv")) {
            Log.w(TAG, "⚠️ Unsupported video format: $videoExt")
        }

        if (audioExt !in listOf("mp4", "m4a", "aac", "mp3", "wav")) {
            Log.w(TAG, "⚠️ Unsupported audio format: $audioExt")
        }

        return true
    }

    private suspend fun getVideoInfo(videoFile: File): Map<String, String> {
        return withContext(Dispatchers.IO) {
            val info = mutableMapOf<String, String>()
            try {
                val command = "-i \"${videoFile.absolutePath}\" -hide_banner"
                val session = FFmpegKit.execute(command)
                val output = session.output ?: ""

                // Parse basic info from FFmpeg output
                if (output.contains("Duration:")) {
                    val durationRegex = "Duration: ([^,]+)".toRegex()
                    val match = durationRegex.find(output)
                    match?.groupValues?.get(1)?.let {
                        info["duration"] = it.trim()
                    }
                }

                if (output.contains("Video:")) {
                    info["hasVideo"] = "true"
                }

                if (output.contains("Audio:")) {
                    info["hasAudio"] = "true"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get video info", e)
            }
            info
        }
    }

    private suspend fun getAudioInfo(audioFile: File): Map<String, String> {
        return withContext(Dispatchers.IO) {
            val info = mutableMapOf<String, String>()
            try {
                val command = "-i \"${audioFile.absolutePath}\" -hide_banner"
                val session = FFmpegKit.execute(command)
                val output = session.output ?: ""

                if (output.contains("Duration:")) {
                    val durationRegex = "Duration: ([^,]+)".toRegex()
                    val match = durationRegex.find(output)
                    match?.groupValues?.get(1)?.let {
                        info["duration"] = it.trim()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get audio info", e)
            }
            info
        }
    }

    private fun performEnhancedMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        options: MergeOptions,
        onProgress: ((Int, String) -> Unit)?,
        onComplete: (File?) -> Unit
    ) {
        val command = buildEnhancedCommand(videoFile, audioFile, outputFile, options)

        Log.d(TAG, "🎬 Enhanced FFmpeg command: $command")
        onProgress?.invoke(35, "Merging audio and video...")

        try {
            FFmpegKit.executeAsync(command) { session ->
                handleMergeResult(session, outputFile, videoFile, audioFile, options, onProgress, onComplete)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during FFmpeg execution", e)
            onComplete(null)
        }
    }

    private fun buildEnhancedCommand(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        options: MergeOptions
    ): String {
        return buildString {
            // Input files
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")

            if (options.replaceAudio) {
                // Simple replacement mode
                append("-c:v copy ")
                append("-c:a aac ")
                append("-b:a 128k ")
                append("-map 0:v:0 ")
                append("-map 1:a:0 ")

                if (options.syncOffset != 0.0f) {
                    append("-itsoffset ${options.syncOffset} ")
                }
            } else {
                // Mix both audio tracks
                append("-c:v copy ")
                append("-filter_complex \"")

                // Audio mixing with volume controls
                append("[1:a]volume=${options.audioVolume}")

                if (options.fadeIn > 0) {
                    append(",afade=t=in:d=${options.fadeIn}")
                }

                if (options.fadeOut > 0) {
                    append(",afade=t=out:d=${options.fadeOut}")
                }

                if (options.syncOffset != 0.0f) {
                    append(",adelay=${(options.syncOffset * 1000).toInt()}|${(options.syncOffset * 1000).toInt()}")
                }

                append("[audio_processed];")
                append("[0:a]volume=${options.videoVolume}[video_audio];")
                append("[video_audio][audio_processed]amix=inputs=2:duration=shortest")
                append("\" ")

                append("-c:a aac ")
                append("-b:a 192k ")
                append("-map 0:v:0 ")
            }

            // Common options
            append("-shortest ")
            append("-avoid_negative_ts make_zero ")
            append("-fflags +genpts ")
            append("-y ")
            append("\"${outputFile.absolutePath}\"")
        }
    }

    private fun handleMergeResult(
        session: FFmpegSession,
        outputFile: File,
        videoFile: File,
        audioFile: File,
        options: MergeOptions,
        onProgress: ((Int, String) -> Unit)?,
        onComplete: (File?) -> Unit
    ) {
        val returnCode = session.returnCode
        val output = session.output
        val logs = session.logsAsString

        Log.d(TAG, "🎬 FFmpeg execution completed")
        Log.d(TAG, "Return code: $returnCode")
        Log.d(TAG, "Session state: ${session.state}")

        if (ReturnCode.isSuccess(returnCode)) {
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Enhanced merge successful!")
                Log.d(TAG, "Output file: ${outputFile.absolutePath}")
                Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                onProgress?.invoke(100, "Merge completed successfully!")

                // Brief delay to show completion
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    onComplete(outputFile)
                }
            } else {
                Log.e(TAG, "❌ Output file is empty or doesn't exist")
                Log.e(TAG, "FFmpeg output: $output")
                onProgress?.invoke(50, "Trying alternative method...")

                // Try fallback method
                tryFallbackMerge(videoFile, audioFile, outputFile, onProgress, onComplete)
            }
        } else {
            Log.e(TAG, "❌ FFmpeg failed with return code: $returnCode")
            Log.e(TAG, "FFmpeg logs: $logs")
            onProgress?.invoke(50, "Trying alternative method...")

            // Try fallback method
            tryFallbackMerge(videoFile, audioFile, outputFile, onProgress, onComplete)
        }
    }

    private fun tryFallbackMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Int, String) -> Unit)?,
        onComplete: (File?) -> Unit
    ) {
        Log.d(TAG, "🔄 Trying fallback merge method...")
        onProgress?.invoke(60, "Using fallback method...")

        // Delete the failed output file
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Simple fallback command
        val fallbackCommand = buildString {
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")
            append("-c:v copy ")
            append("-c:a aac ")
            append("-b:a 128k ")
            append("-map 0:v ")
            append("-map 1:a ")
            append("-shortest ")
            append("-y ")
            append("\"${outputFile.absolutePath}\"")
        }

        Log.d(TAG, "🎬 Fallback FFmpeg command: $fallbackCommand")
        onProgress?.invoke(75, "Processing with basic method...")

        FFmpegKit.executeAsync(fallbackCommand) { session ->
            val returnCode = session.returnCode
            val output = session.output

            if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Fallback merge successful!")
                Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                onProgress?.invoke(100, "Merge completed!")

                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    onComplete(outputFile)
                }
            } else {
                Log.e(TAG, "❌ Fallback merge also failed")
                Log.e(TAG, "Return code: $returnCode")
                Log.e(TAG, "Output: $output")
                onProgress?.invoke(0, "Merge failed")
                onComplete(null)
            }
        }
    }

    // Utility function to create a simple merge with default settings
    fun quickMerge(
        videoFile: File,
        audioFile: File,
        onProgress: ((Int, String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        mergeAudioVideo(
            videoFile = videoFile,
            audioFile = audioFile,
            options = MergeOptions(replaceAudio = true),
            onProgress = onProgress,
            onComplete = onComplete
        )
    }

    // Advanced merge with custom audio mixing
    fun advancedMerge(
        videoFile: File,
        audioFile: File,
        audioVolume: Float = 1.0f,
        keepVideoAudio: Boolean = true,
        syncOffset: Float = 0.0f,
        onProgress: ((Int, String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        mergeAudioVideo(
            videoFile = videoFile,
            audioFile = audioFile,
            options = MergeOptions(
                audioVolume = audioVolume,
                videoVolume = if (keepVideoAudio) 0.3f else 0.0f,
                syncOffset = syncOffset,
                replaceAudio = !keepVideoAudio
            ),
            onProgress = onProgress,
            onComplete = onComplete
        )
    }
}