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
        val videoVolume: Float = 0.5f,
        val syncOffset: Float = 0.0f, // Audio sync offset in seconds
        val fadeIn: Float = 0.0f,
        val fadeOut: Float = 0.0f,
        val replaceAudio: Boolean = false,
        // New timeline editing options
        val videoStartTrim: Long = 0L, // Video start trim in milliseconds
        val videoEndTrim: Long = 0L,   // Video end trim in milliseconds (0 = no trim)
        val audioStartTrim: Long = 0L, // Audio start trim in milliseconds
        val audioEndTrim: Long = 0L,   // Audio end trim in milliseconds (0 = no trim)
        val audioOffset: Long = 0L     // Audio offset in milliseconds
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
                Log.d(TAG, "🎬 Starting enhanced merge process with timeline editing...")

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

                Log.d(TAG, "📁 Files ready for merge with timeline options:")
                Log.d(TAG, "Video: ${videoFile.absolutePath} (${videoFile.length()} bytes)")
                Log.d(TAG, "Audio: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                Log.d(TAG, "Output: ${outputFile.absolutePath}")
                Log.d(TAG, "Timeline options: $options")

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(15, "Preparing timeline merge...")
                }

                // Get media information first
                val videoInfo = getVideoInfo(videoFile)
                val audioInfo = getAudioInfo(audioFile)

                Log.d(TAG, "Video info: $videoInfo")
                Log.d(TAG, "Audio info: $audioInfo")

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(25, "Processing timeline edits...")
                }

                // Perform timeline-aware merge
                withContext(Dispatchers.Main) {
                    performTimelineAwareMerge(
                        videoFile,
                        audioFile,
                        outputFile,
                        options,
                        onProgress,
                        onComplete
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in timeline merge process", e)
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

    private fun performTimelineAwareMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        options: MergeOptions,
        onProgress: ((Int, String) -> Unit)?,
        onComplete: (File?) -> Unit
    ) {
        val command = buildTimelineAwareCommand(videoFile, audioFile, outputFile, options)

        Log.d(TAG, "🎬 Timeline-aware FFmpeg command: $command")
        onProgress?.invoke(35, "Merging with timeline edits...")

        try {
            FFmpegKit.executeAsync(command) { session ->
                handleMergeResult(session, outputFile, videoFile, audioFile, options, onProgress, onComplete)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during timeline FFmpeg execution", e)
            onComplete(null)
        }
    }

    private fun buildTimelineAwareCommand(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        options: MergeOptions
    ): String {
        return buildString {
            // Input files with potential seek and duration
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")

            // Build complex filter for timeline editing
            append("-filter_complex \"")

            var filterIndex = 0
            val filters = mutableListOf<String>()

            // Video processing
            var videoLabel = "[0:v]"

            // Video trimming
            if (options.videoStartTrim > 0 || options.videoEndTrim > 0) {
                val startSeconds = options.videoStartTrim / 1000.0
                val videoFilter = if (options.videoEndTrim > 0) {
                    val endSeconds = options.videoEndTrim / 1000.0
                    val duration = endSeconds - startSeconds
                    "${videoLabel}trim=start=${startSeconds}:duration=${duration},setpts=PTS-STARTPTS[v${filterIndex}]"
                } else {
                    "${videoLabel}trim=start=${startSeconds},setpts=PTS-STARTPTS[v${filterIndex}]"
                }
                filters.add(videoFilter)
                videoLabel = "[v${filterIndex}]"
                filterIndex++
            }

            // Audio processing
            var audioLabel = "[1:a]"

            // Audio trimming
            if (options.audioStartTrim > 0 || options.audioEndTrim > 0) {
                val startSeconds = options.audioStartTrim / 1000.0
                val audioFilter = if (options.audioEndTrim > 0) {
                    val endSeconds = options.audioEndTrim / 1000.0
                    val duration = endSeconds - startSeconds
                    "${audioLabel}atrim=start=${startSeconds}:duration=${duration},asetpts=PTS-STARTPTS[a${filterIndex}]"
                } else {
                    "${audioLabel}atrim=start=${startSeconds},asetpts=PTS-STARTPTS[a${filterIndex}]"
                }
                filters.add(audioFilter)
                audioLabel = "[a${filterIndex}]"
                filterIndex++
            }

            // Audio offset/delay
            if (options.audioOffset != 0L) {
                val delayMs = options.audioOffset
                filters.add("${audioLabel}adelay=${delayMs}|${delayMs}[a${filterIndex}]")
                audioLabel = "[a${filterIndex}]"
                filterIndex++
            }

            // Audio volume adjustment
            if (options.audioVolume != 1.0f) {
                filters.add("${audioLabel}volume=${options.audioVolume}[a${filterIndex}]")
                audioLabel = "[a${filterIndex}]"
                filterIndex++
            }

            // Video audio processing (if not replacing)
            var videoAudioLabel = ""
            if (!options.replaceAudio) {
                videoAudioLabel = "[0:a]"

                // Video audio trimming (match video trimming)
                if (options.videoStartTrim > 0 || options.videoEndTrim > 0) {
                    val startSeconds = options.videoStartTrim / 1000.0
                    val videoAudioFilter = if (options.videoEndTrim > 0) {
                        val endSeconds = options.videoEndTrim / 1000.0
                        val duration = endSeconds - startSeconds
                        "${videoAudioLabel}atrim=start=${startSeconds}:duration=${duration},asetpts=PTS-STARTPTS[va${filterIndex}]"
                    } else {
                        "${videoAudioLabel}atrim=start=${startSeconds},asetpts=PTS-STARTPTS[va${filterIndex}]"
                    }
                    filters.add(videoAudioFilter)
                    videoAudioLabel = "[va${filterIndex}]"
                    filterIndex++
                }

                // Video audio volume
                if (options.videoVolume != 1.0f) {
                    filters.add("${videoAudioLabel}volume=${options.videoVolume}[va${filterIndex}]")
                    videoAudioLabel = "[va${filterIndex}]"
                    filterIndex++
                }

                // Mix audio tracks
                filters.add("${videoAudioLabel}${audioLabel}amix=inputs=2:duration=shortest[aout]")
            } else {
                // Just use the processed audio track
                filters.add("${audioLabel}acopy[aout]")
            }

            // Apply fade effects if specified
            if (options.fadeIn > 0 || options.fadeOut > 0) {
                var fadeLabel = "[aout]"
                if (options.fadeIn > 0) {
                    filters.add("${fadeLabel}afade=t=in:d=${options.fadeIn}[af${filterIndex}]")
                    fadeLabel = "[af${filterIndex}]"
                    filterIndex++
                }
                if (options.fadeOut > 0) {
                    filters.add("${fadeLabel}afade=t=out:d=${options.fadeOut}[aout]")
                }
            }

            // Combine all filters
            append(filters.joinToString(";"))
            append("\" ")

            // Map outputs
            append("-map ${videoLabel} ")
            append("-map [aout] ")

            // Codec settings
            append("-c:v copy ")
            append("-c:a aac ")
            append("-b:a 192k ")

            // Additional options
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

        Log.d(TAG, "🎬 Timeline FFmpeg execution completed")
        Log.d(TAG, "Return code: $returnCode")
        Log.d(TAG, "Session state: ${session.state}")

        if (ReturnCode.isSuccess(returnCode)) {
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Timeline merge successful!")
                Log.d(TAG, "Output file: ${outputFile.absolutePath}")
                Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                onProgress?.invoke(100, "Timeline merge completed successfully!")

                // Brief delay to show completion
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    onComplete(outputFile)
                }
            } else {
                Log.e(TAG, "❌ Timeline output file is empty or doesn't exist")
                Log.e(TAG, "FFmpeg output: $output")
                onProgress?.invoke(50, "Trying fallback method...")

                // Try simple fallback method
                trySimpleFallbackMerge(videoFile, audioFile, outputFile, options, onProgress, onComplete)
            }
        } else {
            Log.e(TAG, "❌ Timeline FFmpeg failed with return code: $returnCode")
            Log.e(TAG, "FFmpeg logs: $logs")
            onProgress?.invoke(50, "Trying fallback method...")

            // Try simple fallback method
            trySimpleFallbackMerge(videoFile, audioFile, outputFile, options, onProgress, onComplete)
        }
    }

    private fun trySimpleFallbackMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        options: MergeOptions,
        onProgress: ((Int, String) -> Unit)?,
        onComplete: (File?) -> Unit
    ) {
        Log.d(TAG, "🔄 Trying simple fallback merge method...")
        onProgress?.invoke(60, "Using fallback method...")

        // Delete the failed output file
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Simple fallback command without timeline features
        val fallbackCommand = buildString {
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")
            append("-c:v copy ")
            append("-c:a aac ")
            append("-b:a 128k ")

            if (options.replaceAudio) {
                append("-map 0:v ")
                append("-map 1:a ")
            } else {
                append("-filter_complex \"[0:a]volume=${options.videoVolume}[va];[1:a]volume=${options.audioVolume}[aa];[va][aa]amix=inputs=2:duration=shortest\" ")
                append("-map 0:v ")
            }

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

    // Advanced merge with timeline editing
    fun timelineEditMerge(
        videoFile: File,
        audioFile: File,
        videoStartTrim: Long = 0L,
        videoEndTrim: Long = 0L,
        audioStartTrim: Long = 0L,
        audioEndTrim: Long = 0L,
        audioOffset: Long = 0L,
        audioVolume: Float = 1.0f,
        videoVolume: Float = 0.3f,
        replaceAudio: Boolean = false,
        onProgress: ((Int, String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        mergeAudioVideo(
            videoFile = videoFile,
            audioFile = audioFile,
            options = MergeOptions(
                audioVolume = audioVolume,
                videoVolume = videoVolume,
                videoStartTrim = videoStartTrim,
                videoEndTrim = videoEndTrim,
                audioStartTrim = audioStartTrim,
                audioEndTrim = audioEndTrim,
                audioOffset = audioOffset,
                replaceAudio = replaceAudio
            ),
            onProgress = onProgress,
            onComplete = onComplete
        )
    }
}