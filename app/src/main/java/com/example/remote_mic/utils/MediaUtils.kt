package com.example.remote_mic.utils


import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object MediaUtils {
    private const val TAG = "MediaUtils"

    /**
     * Save a video file to the device's gallery
     */
    suspend fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        onSuccess: (Uri?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val displayName = "RemoteMic_Video_$timestamp.mp4"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore for Android 10+
                    saveVideoToMediaStore(context, videoFile, displayName, onSuccess, onError)
                } else {
                    // Use external storage for older versions
                    saveVideoToExternalStorage(context, videoFile, displayName, onSuccess, onError)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video to gallery", e)
                withContext(Dispatchers.Main) {
                    onError("Failed to save video: ${e.message}")
                }
            }
        }
    }

    private suspend fun saveVideoToMediaStore(
        context: Context,
        videoFile: File,
        displayName: String,
        onSuccess: (Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RemoteMic")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                // Copy file to MediaStore
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(videoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Mark as not pending
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "✅ Video saved to gallery: $uri")

                withContext(Dispatchers.Main) {
                    onSuccess(uri)
                    Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("Failed to create media store entry")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore", e)
            withContext(Dispatchers.Main) {
                onError("Failed to save to gallery: ${e.message}")
            }
        }
    }

    private suspend fun saveVideoToExternalStorage(
        context: Context,
        videoFile: File,
        displayName: String,
        onSuccess: (Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val appDir = File(moviesDir, "RemoteMic")

            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val destinationFile = File(appDir, displayName)

            // Copy file
            FileInputStream(videoFile).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Add to MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DATA, destinationFile.absolutePath)
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            // Notify media scanner
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(destinationFile)
            context.sendBroadcast(intent)

            Log.d(TAG, "✅ Video saved to external storage: ${destinationFile.absolutePath}")

            withContext(Dispatchers.Main) {
                onSuccess(uri)
                Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to external storage", e)
            withContext(Dispatchers.Main) {
                onError("Failed to save to storage: ${e.message}")
            }
        }
    }

    /**
     * Share a video file using system sharing
     */
    fun shareVideo(context: Context, videoFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    videoFile
                )
            } else {
                Uri.fromFile(videoFile)
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Video")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing video", e)
            Toast.makeText(context, "Failed to share video", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open video in gallery app
     */
    fun openVideoInGallery(context: Context, uri: Uri?) {
        try {
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "No app found to open video", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening video", e)
            Toast.makeText(context, "Failed to open video", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Delete temporary files
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val tempDir = File(context.getExternalFilesDir(null), "temp")
            if (tempDir.exists()) {
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                        // Delete files older than 24 hours
                        file.delete()
                        Log.d(TAG, "Deleted temp file: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning temp files", e)
        }
    }

    /**
     * Get file size in human readable format
     */
    fun getFileSize(file: File): String {
        val bytes = file.length()
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Format duration from milliseconds to readable format
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }
}