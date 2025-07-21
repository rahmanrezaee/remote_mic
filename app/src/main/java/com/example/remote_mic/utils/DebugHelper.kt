package com.example.remote_mic.utils

import android.content.Context
import android.util.Log
import java.io.File

object DebugHelper {
    private const val TAG = "DebugHelper"

    fun logAllFiles(context: Context) {
        Log.d(TAG, "=== DEBUGGING ALL FILES ===")

        val externalDir = context.getExternalFilesDir(null)
        Log.d(TAG, "External files dir: ${externalDir?.absolutePath}")

        val directories = listOf(
            "RemoteMicVideos",
            "RemoteMicAudios",
            "ReceivedFiles"
        )

        directories.forEach { dirName ->
            val dir = File(externalDir, dirName)
            Log.d(TAG, "Directory: $dirName")
            Log.d(TAG, "  Path: ${dir.absolutePath}")
            Log.d(TAG, "  Exists: ${dir.exists()}")
            Log.d(TAG, "  Can read: ${dir.canRead()}")
            Log.d(TAG, "  Can write: ${dir.canWrite()}")

            if (dir.exists()) {
                val files = dir.listFiles()
                Log.d(TAG, "  Files count: ${files?.size ?: 0}")

                files?.forEach { file ->
                    Log.d(TAG, "    File: ${file.name}")
                    Log.d(TAG, "      Size: ${file.length()} bytes")
                    Log.d(TAG, "      Last modified: ${java.util.Date(file.lastModified())}")
                    Log.d(TAG, "      Can read: ${file.canRead()}")
                    Log.d(TAG, "      Extension: ${file.extension}")
                }
            }
        }

        // Also check root external files dir
        externalDir?.listFiles()?.forEach { file ->
            if (file.isFile()) {
                Log.d(TAG, "Root file: ${file.name} (${file.length()} bytes)")
            } else if (file.isDirectory()) {
                Log.d(TAG, "Root directory: ${file.name}")
            }
        }

        Log.d(TAG, "=== END DEBUG FILES ===")
    }

    fun createTestFiles(context: Context) {
        Log.d(TAG, "Creating test files...")

        try {
            val videosDir = File(context.getExternalFilesDir(null), "RemoteMicVideos")
            val audiosDir = File(context.getExternalFilesDir(null), "RemoteMicAudios")
            val receivedDir = File(context.getExternalFilesDir(null), "ReceivedFiles")

            videosDir.mkdirs()
            audiosDir.mkdirs()
            receivedDir.mkdirs()

            // Create test video file
            val testVideo = File(videosDir, "test_video.mp4")
            testVideo.writeText("This is a test video file")
            Log.d(TAG, "Created test video: ${testVideo.absolutePath}")

            // Create test audio file
            val testAudio = File(audiosDir, "test_audio.m4a")
            testAudio.writeText("This is a test audio file")
            Log.d(TAG, "Created test audio: ${testAudio.absolutePath}")

            // Create test received file
            val testReceived = File(receivedDir, "test_received.m4a")
            testReceived.writeText("This is a test received audio file")
            Log.d(TAG, "Created test received: ${testReceived.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating test files", e)
        }
    }

    fun clearAllFiles(context: Context) {
        Log.d(TAG, "Clearing all files...")

        val directories = listOf(
            "RemoteMicVideos",
            "RemoteMicAudios",
            "ReceivedFiles"
        )

        directories.forEach { dirName ->
            val dir = File(context.getExternalFilesDir(null), dirName)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    val deleted = file.delete()
                    Log.d(TAG, "Deleted ${file.name}: $deleted")
                }
            }
        }
    }
}