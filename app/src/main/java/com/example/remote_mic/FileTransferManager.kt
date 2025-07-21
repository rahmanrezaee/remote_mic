package com.example.remote_mic

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class TransferState(
    val isTransferring: Boolean = false,
    val transferProgress: Float = 0f,
    val transferredFiles: List<File> = emptyList(),
    val errorMessage: String? = null
)

class FileTransferManager(
    private val context: Context,
    private val sendPayload: (String, Payload) -> Unit,
    private val onFileReceived: (File) -> Unit
) {
    companion object {
        private const val TAG = "FileTransferManager"
    }

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val receivedFilesDir by lazy {
        File(context.getExternalFilesDir(null), "ReceivedFiles").apply {
            if (!exists()) mkdirs()
        }
    }

    private val transferJobs = mutableMapOf<String, Job>()
    private val pendingFiles = mutableMapOf<Long, String>() // payload id to filename

    suspend fun sendFile(endpointId: String, file: File) {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.path}")
            updateState { copy(errorMessage = "File does not exist: ${file.path}") }
            return
        }

        Log.d(TAG, "Starting file transfer: ${file.name}, size: ${file.length()}")
        updateState { copy(isTransferring = true, transferProgress = 0f, errorMessage = null) }

        try {
            // First send file metadata as bytes
            val metadata = createFileMetadata(file)
            val metadataPayload = Payload.fromBytes(metadata.toByteArray())
            sendPayload(endpointId, metadataPayload)
            Log.d(TAG, "Sent file metadata: $metadata")

            // Then send the actual file
            val filePayload = Payload.fromFile(file)
            sendPayload(endpointId, filePayload)
            Log.d(TAG, "File transfer initiated for: ${file.name} with payload ID: ${filePayload.id}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file: ${file.name}", e)
            updateState {
                copy(
                    isTransferring = false,
                    errorMessage = "Failed to send file: ${e.message}"
                )
            }
        }
    }

    fun receiveFile(payload: Payload) {
        when (payload.type) {
            Payload.Type.FILE -> {
                Log.d(TAG, "Receiving file payload with ID: ${payload.id}")
                payload.asFile()?.let { filePayload ->
                    val transferJob = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            updateState { copy(isTransferring = true, transferProgress = 0f) }

                            // Get filename from pending files or generate one
                            val fileName = pendingFiles[payload.id]
                                ?: "received_file_${System.currentTimeMillis()}"

                            pendingFiles.remove(payload.id)

                            val outputFile = File(receivedFilesDir, fileName)
                            Log.d(TAG, "Saving received file to: ${outputFile.absolutePath}")

                            // Copy file from payload to our directory
                            filePayload.asJavaFile()?.let { inputFile ->
                                Log.d(TAG, "Input file exists: ${inputFile.exists()}, size: ${inputFile.length()}")

                                if (inputFile.exists() && inputFile.length() > 0) {
                                    inputFile.copyTo(outputFile, overwrite = true)
                                    Log.d(TAG, "File copied successfully. Output size: ${outputFile.length()}")

                                    withContext(Dispatchers.Main) {
                                        updateState {
                                            copy(
                                                isTransferring = false,
                                                transferProgress = 1f,
                                                transferredFiles = transferredFiles + outputFile,
                                                errorMessage = null
                                            )
                                        }
                                        onFileReceived(outputFile)
                                    }
                                } else {
                                    throw Exception("Input file is empty or doesn't exist")
                                }
                            } ?: throw Exception("Could not get Java file from payload")

                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to receive file", e)
                            withContext(Dispatchers.Main) {
                                updateState {
                                    copy(
                                        isTransferring = false,
                                        errorMessage = "Failed to receive file: ${e.message}"
                                    )
                                }
                            }
                        }
                    }

                    transferJobs[payload.id.toString()] = transferJob
                }
            }
            Payload.Type.BYTES -> {
                // Handle file metadata
                payload.asBytes()?.let { bytes ->
                    try {
                        val metadataString = String(bytes)
                        Log.d(TAG, "Received file metadata: $metadataString")

                        // Parse metadata to get filename and payload ID
                        val fileName = extractFileName(metadataString)
                        val payloadId = extractPayloadId(metadataString)

                        if (fileName != null && payloadId != null) {
                            pendingFiles[payloadId] = fileName
                            Log.d(TAG, "Mapped payload ID $payloadId to filename: $fileName")
                        } else {
                            Log.e(TAG, "Failed to extract filename or payload ID from metadata")
                            updateState {
                                copy(
                                    errorMessage = "Invalid file metadata received"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse metadata", e)
                    }
                }
            }
        }
    }

    fun updateTransferProgress(payloadId: Long, progress: Float) {
        Log.d(TAG, "Transfer progress for payload $payloadId: ${(progress * 100).toInt()}%")
        updateState { copy(transferProgress = progress) }
    }

    fun onTransferComplete(payloadId: Long, success: Boolean) {
        transferJobs[payloadId.toString()]?.cancel()
        transferJobs.remove(payloadId.toString())

        if (success) {
            Log.d(TAG, "Transfer completed successfully for payload: $payloadId")
            updateState { copy(isTransferring = false, transferProgress = 1f) }
        } else {
            Log.e(TAG, "Transfer failed for payload: $payloadId")
            updateState {
                copy(
                    isTransferring = false,
                    errorMessage = "Transfer failed for payload: $payloadId"
                )
            }
        }
    }

    private fun createFileMetadata(file: File): String {
        return """
            {
                "fileName": "${file.name}",
                "fileSize": ${file.length()},
                "mimeType": "${getMimeType(file)}",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()
    }

    private fun extractFileName(metadata: String?): String? {
        return try {
            metadata?.let {
                val fileNameStart = it.indexOf("\"fileName\": \"") + 13
                val fileNameEnd = it.indexOf("\"", fileNameStart)
                if (fileNameStart > 12 && fileNameEnd > fileNameStart) {
                    it.substring(fileNameStart, fileNameEnd)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse filename from metadata", e)
            null
        }
    }

    private fun extractPayloadId(metadata: String?): Long? {
        return try {
            // For this implementation, we'll use timestamp as a simple ID
            // In a real implementation, you'd include the actual payload ID in metadata
            metadata?.let {
                val timestampStart = it.indexOf("\"timestamp\": ") + 13
                val timestampEnd = it.indexOf("\n", timestampStart)
                val endIndex = if (timestampEnd == -1) it.length else timestampEnd
                val timestampStr = it.substring(timestampStart, endIndex).trim().removeSuffix("}")
                timestampStr.toLongOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse payload ID from metadata", e)
            null
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4", "mov", "avi" -> "video/mp4"
            "m4a", "aac", "mp3", "wav" -> "audio/mpeg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    fun clearTransferredFiles() {
        updateState { copy(transferredFiles = emptyList()) }
    }

    private fun updateState(update: TransferState.() -> TransferState) {
        _state.value = _state.value.update()
    }

    fun cleanup() {
        transferJobs.values.forEach { it.cancel() }
        transferJobs.clear()
        pendingFiles.clear()
    }
}