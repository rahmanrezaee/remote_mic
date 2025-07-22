package com.example.remote_mic.manager

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File

/**
 * AudioVideoMerger handles audio and video processing using FFmpeg
 * Supports merging, trimming, and format conversion
 */
class AudioVideoMerger(private val context: Context) {
    companion object {
        private const val TAG = "AudioVideoMerger"
    }

    private val mergedDirectory by lazy {
        File(context.getExternalFilesDir(null), "merged").apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempDirectory by lazy {
        File(context.getExternalFilesDir(null), "temp").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Merge audio and video files into a single MP4 file
     */
    fun mergeAudioVideo(
        videoFile: File,
        audioFile: File,
        outputFileName: String = "merged_${System.currentTimeMillis()}.mp4",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🎬 Starting merge process...")

                // Validate files
                withContext(Dispatchers.Main) { onProgress?.invoke("Validating files...") }

                if (!validateFile(videoFile, "Video")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                if (!validateFile(audioFile, "Audio")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                // Create output file
                val outputFile = File(mergedDirectory, outputFileName)
                if (outputFile.exists()) outputFile.delete()

                Log.d(TAG, "📁 Files ready for merge:")
                Log.d(TAG, "Video: ${videoFile.absolutePath} (${videoFile.length()} bytes)")
                Log.d(TAG, "Audio: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                Log.d(TAG, "Output: ${outputFile.absolutePath}")

                withContext(Dispatchers.Main) { onProgress?.invoke("Merging files...") }

                // Switch to main thread for FFmpeg execution
                withContext(Dispatchers.Main) {
                    performMerge(videoFile, audioFile, outputFile, onProgress, onComplete)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in merge process", e)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Merge failed: ${e.message}")
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Trim audio file to specified duration
     */
    fun trimAudio(
        audioFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        outputFileName: String = "trimmed_audio_${System.currentTimeMillis()}.mp4",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!validateFile(audioFile, "Audio")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                val outputFile = File(tempDirectory, outputFileName)
                if (outputFile.exists()) outputFile.delete()

                val startSeconds = startTimeMs / 1000.0
                val duration = (endTimeMs - startTimeMs) / 1000.0

                withContext(Dispatchers.Main) { onProgress?.invoke("Trimming audio...") }

                val command = buildString {
                    append("-i \"${audioFile.absolutePath}\" ")
                    append("-ss $startSeconds ")
                    append("-t $duration ")
                    append("-c:a aac ")
                    append("-b:a 128k ")
                    append("-y ")
                    append("\"${outputFile.absolutePath}\"")
                }

                Log.d(TAG, "🎵 Audio trim command: $command")

                withContext(Dispatchers.Main) {
                    FFmpegKit.executeAsync(command) { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                            Log.d(TAG, "✅ Audio trim successful!")
                            onProgress?.invoke("Audio trimmed successfully!")
                            onComplete(outputFile)
                        } else {
                            Log.e(TAG, "❌ Audio trim failed")
                            onProgress?.invoke("Audio trim failed")
                            onComplete(null)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error trimming audio", e)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Trim failed: ${e.message}")
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Trim video file to specified duration
     */
    fun trimVideo(
        videoFile: File,
        startTimeMs: Long,
        endTimeMs: Long,
        outputFileName: String = "trimmed_video_${System.currentTimeMillis()}.mp4",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!validateFile(videoFile, "Video")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                val outputFile = File(tempDirectory, outputFileName)
                if (outputFile.exists()) outputFile.delete()

                val startSeconds = startTimeMs / 1000.0
                val duration = (endTimeMs - startTimeMs) / 1000.0

                withContext(Dispatchers.Main) { onProgress?.invoke("Trimming video...") }

                val command = buildString {
                    append("-i \"${videoFile.absolutePath}\" ")
                    append("-ss $startSeconds ")
                    append("-t $duration ")
                    append("-c:v libx264 ")
                    append("-c:a aac ")
                    append("-preset fast ")
                    append("-crf 23 ")
                    append("-y ")
                    append("\"${outputFile.absolutePath}\"")
                }

                Log.d(TAG, "🎥 Video trim command: $command")

                withContext(Dispatchers.Main) {
                    FFmpegKit.executeAsync(command) { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                            Log.d(TAG, "✅ Video trim successful!")
                            onProgress?.invoke("Video trimmed successfully!")
                            onComplete(outputFile)
                        } else {
                            Log.e(TAG, "❌ Video trim failed")
                            onProgress?.invoke("Video trim failed")
                            onComplete(null)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error trimming video", e)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Trim failed: ${e.message}")
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Convert audio format
     */
    fun convertAudio(
        audioFile: File,
        targetFormat: AudioFormat = AudioFormat.MP4_AAC,
        outputFileName: String = "converted_${System.currentTimeMillis()}.${targetFormat.extension}",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!validateFile(audioFile, "Audio")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                val outputFile = File(tempDirectory, outputFileName)
                if (outputFile.exists()) outputFile.delete()

                withContext(Dispatchers.Main) { onProgress?.invoke("Converting audio format...") }

                val command = buildString {
                    append("-i \"${audioFile.absolutePath}\" ")
                    append("-c:a ${targetFormat.codec} ")
                    append("-b:a ${targetFormat.bitrate} ")
                    append("-ar ${targetFormat.sampleRate} ")
                    append("-y ")
                    append("\"${outputFile.absolutePath}\"")
                }

                Log.d(TAG, "🔄 Audio convert command: $command")

                withContext(Dispatchers.Main) {
                    FFmpegKit.executeAsync(command) { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                            Log.d(TAG, "✅ Audio conversion successful!")
                            onProgress?.invoke("Audio converted successfully!")
                            onComplete(outputFile)
                        } else {
                            Log.e(TAG, "❌ Audio conversion failed")
                            onProgress?.invoke("Audio conversion failed")
                            onComplete(null)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error converting audio", e)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Conversion failed: ${e.message}")
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Extract audio from video file
     */
    fun extractAudio(
        videoFile: File,
        outputFileName: String = "extracted_audio_${System.currentTimeMillis()}.mp4",
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!validateFile(videoFile, "Video")) {
                    withContext(Dispatchers.Main) { onComplete(null) }
                    return@launch
                }

                val outputFile = File(tempDirectory, outputFileName)
                if (outputFile.exists()) outputFile.delete()

                withContext(Dispatchers.Main) { onProgress?.invoke("Extracting audio from video...") }

                val command = buildString {
                    append("-i \"${videoFile.absolutePath}\" ")
                    append("-vn ")  // No video
                    append("-c:a aac ")
                    append("-b:a 128k ")
                    append("-y ")
                    append("\"${outputFile.absolutePath}\"")
                }

                Log.d(TAG, "🎵 Audio extract command: $command")

                withContext(Dispatchers.Main) {
                    FFmpegKit.executeAsync(command) { session ->
                        val returnCode = session.returnCode
                        if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                            Log.d(TAG, "✅ Audio extraction successful!")
                            onProgress?.invoke("Audio extracted successfully!")
                            onComplete(outputFile)
                        } else {
                            Log.e(TAG, "❌ Audio extraction failed")
                            onProgress?.invoke("Audio extraction failed")
                            onComplete(null)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error extracting audio", e)
                withContext(Dispatchers.Main) {
                    onProgress?.invoke("Extraction failed: ${e.message}")
                    onComplete(null)
                }
            }
        }
    }

    private fun performMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        onProgress?.invoke("Processing with FFmpeg...")

        // Enhanced FFmpeg command for better audio sync and quality
        val command = buildString {
            append("-i \"${videoFile.absolutePath}\" ")
            append("-i \"${audioFile.absolutePath}\" ")
            append("-c:v copy ")  // Copy video stream as-is (no re-encoding)
            append("-c:a aac ")   // Re-encode audio as AAC
            append("-b:a 128k ")  // Audio bitrate 128kbps
            append("-ar 44100 ")  // Audio sample rate
            append("-map 0:v:0 ") // Map first video stream from first input
            append("-map 1:a:0 ") // Map first audio stream from second input
            append("-shortest ")  // End when shortest stream ends
            append("-avoid_negative_ts make_zero ") // Fix timestamp issues
            append("-async 1 ")   // Audio sync method
            append("-vsync cfr ") // Constant frame rate for video
            append("-y ")         // Overwrite output file
            append("\"${outputFile.absolutePath}\"")
        }

        Log.d(TAG, "🎬 FFmpeg command: $command")

        try {
            FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode
                val output = session.output

                Log.d(TAG, "🎬 FFmpeg execution completed")
                Log.d(TAG, "Return code: $returnCode")
                Log.d(TAG, "Session state: ${session.state}")

                if (ReturnCode.isSuccess(returnCode)) {
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.d(TAG, "✅ Merge successful!")
                        Log.d(TAG, "Output file: ${outputFile.absolutePath}")
                        Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                        onProgress?.invoke("Merge completed successfully!")
                        onComplete(outputFile)
                    } else {
                        Log.e(TAG, "❌ Output file is empty or doesn't exist")
                        Log.e(TAG, "FFmpeg output: $output")
                        onProgress?.invoke("Merge failed - trying alternative method...")
                        tryAlternativeMerge(videoFile, audioFile, outputFile, onProgress, onComplete)
                    }
                } else {
                    Log.e(TAG, "❌ FFmpeg failed with return code: $returnCode")
                    Log.e(TAG, "FFmpeg output: $output")
                    onProgress?.invoke("Primary merge failed - trying alternative...")
                    // Try alternative command
                    tryAlternativeMerge(videoFile, audioFile, outputFile, onProgress, onComplete)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during FFmpeg execution", e)
            onProgress?.invoke("Merge failed with error")
            onComplete(null)
        }
    }

    private fun tryAlternativeMerge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((String) -> Unit)? = null,
        onComplete: (File?) -> Unit
    ) {
        Log.d(TAG, "🔄 Trying alternative merge method...")
        onProgress?.invoke("Trying alternative merge method...")

        // Delete the failed output file
        if (outputFile.exists()) outputFile.delete()

        // Alternative command - simpler approach
        val altCommand = buildString {
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

        Log.d(TAG, "🎬 Alternative FFmpeg command: $altCommand")

        FFmpegKit.executeAsync(altCommand) { session ->
            val returnCode = session.returnCode
            val output = session.output

            if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "✅ Alternative merge successful!")
                Log.d(TAG, "Output size: ${outputFile.length()} bytes")
                onProgress?.invoke("Alternative merge successful!")
                onComplete(outputFile)
            } else {
                Log.e(TAG, "❌ Alternative merge also failed")
                Log.e(TAG, "Return code: $returnCode")
                Log.e(TAG, "Output: $output")
                onProgress?.invoke("All merge attempts failed")
                onComplete(null)
            }
        }
    }

    private fun validateFile(file: File, type: String): Boolean {
        if (!file.exists()) {
            Log.e(TAG, "❌ $type file doesn't exist: ${file.absolutePath}")
            return false
        }
        if (file.length() == 0L) {
            Log.e(TAG, "❌ $type file is empty: ${file.absolutePath}")
            return false
        }
        Log.d(TAG, "✅ $type file validated: ${file.name} (${file.length()} bytes)")
        return true
    }

    /**
     * Get information about media file
     */
    fun getMediaInfo(
        file: File,
        onComplete: (MediaInfo?) -> Unit
    ) {
        if (!validateFile(file, "Media")) {
            onComplete(null)
            return
        }

        val command = "-i \"${file.absolutePath}\""

        FFmpegKit.executeAsync(command) { session ->
            val output = session.allLogsAsString

            // Parse basic info from FFmpeg output
            val duration = extractDuration(output)
            val resolution = extractResolution(output)
            val audioCodec = extractAudioCodec(output)
            val videoCodec = extractVideoCodec(output)

            val mediaInfo = MediaInfo(
                file = file,
                duration = duration,
                resolution = resolution,
                audioCodec = audioCodec,
                videoCodec = videoCodec,
                fileSize = file.length()
            )

            onComplete(mediaInfo)
        }
    }

    private fun extractDuration(output: String): Long {
        val durationRegex = """Duration: (\d{2}):(\d{2}):(\d{2})\.(\d{2})""".toRegex()
        val match = durationRegex.find(output)
        return if (match != null) {
            val hours = match.groupValues[1].toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val centiseconds = match.groupValues[4].toLong()
            (hours * 3600 + minutes * 60 + seconds) * 1000 + centiseconds * 10
        } else {
            0L
        }
    }

    private fun extractResolution(output: String): String {
        val resolutionRegex = """(\d{3,4})x(\d{3,4})""".toRegex()
        val match = resolutionRegex.find(output)
        return match?.value ?: "Unknown"
    }

    private fun extractAudioCodec(output: String): String {
        val audioRegex = """Audio: ([^,\s]+)""".toRegex()
        val match = audioRegex.find(output)
        return match?.groupValues?.get(1) ?: "Unknown"
    }

    private fun extractVideoCodec(output: String): String {
        val videoRegex = """Video: ([^,\s]+)""".toRegex()
        val match = videoRegex.find(output)
        return match?.groupValues?.get(1) ?: "Unknown"
    }

    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles() {
        try {
            tempDirectory.listFiles()?.forEach { file ->
                if (file.isFile() && System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    file.delete()
                    Log.d(TAG, "🧹 Cleaned up old temp file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }
}

// Data classes for media processing
data class MediaInfo(
    val file: File,
    val duration: Long, // in milliseconds
    val resolution: String,
    val audioCodec: String,
    val videoCodec: String,
    val fileSize: Long
)

enum class AudioFormat(
    val extension: String,
    val codec: String,
    val bitrate: String,
    val sampleRate: String
) {
    MP4_AAC("mp4", "aac", "128k", "44100"),
    MP3("mp3", "mp3", "192k", "44100"),
    WAV("wav", "pcm_s16le", "1411k", "44100"),
    M4A("m4a", "aac", "128k", "44100")
}