package com.example.remote_mic.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.remote_mic.managers.AudioVideoMerger
import com.example.remote_mic.utils.MediaUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

// Enhanced data classes with undo functionality
data class TimelineState(
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val zoom: Float = 1f
)

data class AudioSegment(
    val id: String,
    val originalStartTime: Long,
    val originalEndTime: Long,
    val timelineStart: Long,
    val volume: Float = 1.0f
) {
    val originalDuration: Long get() = originalEndTime - originalStartTime
    val timelineEnd: Long get() = timelineStart + originalDuration
}

data class EditState(
    val segments: List<AudioSegment> = emptyList(),
    val selectedSegmentId: String? = null,
    val history: List<List<AudioSegment>> = emptyList(),
    val historyIndex: Int = -1,
    val videoVolume: Float = 1.0f // Add video volume control (0.0 to 2.0)
) {
    val canUndo: Boolean get() = historyIndex >= 0
    val canRedo: Boolean get() = historyIndex < history.size - 1
    val effectiveSelectedSegmentId: String?
        get() = selectedSegmentId ?: segments.firstOrNull()?.id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAudioEditingControls(
    selectedSegmentId: String?,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onVolumeClick: () -> Unit,

    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit controls - left side
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Split button
                IconButton(
                    onClick = onSplit,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF00D4AA), CircleShape)
                ) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = "Split",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Delete button
                val canDelete = selectedSegmentId != null
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (canDelete) Color(0xFFFF6B6B) else Color.Gray.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (canDelete) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Audio segment volume button
                IconButton(
                    onClick = onVolumeClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF74B9FF), CircleShape)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Audio Volume",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }


            }

            // Zoom controls - right side
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onZoomOut,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2D2D2D), CircleShape)
                ) {
                    Icon(
                        Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onZoomIn,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2D2D2D), CircleShape)
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Enhanced ModernVolumeDialog that can handle both audio and video volume
@Composable
fun ModernVolumeDialog(
    title: String,
    volume: Int,
    isVideoVolume: Boolean = false, // Add parameter to distinguish video volume
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isVideoVolume) Icons.Default.RecordVoiceOver else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (isVideoVolume) Color(0xFFE17055) else Color(0xFF74B9FF),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Volume display with different colors for video volume
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    (if (isVideoVolume) Color(0xFFE17055) else Color(0xFF74B9FF))
                                        .copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$volume%",
                                color = if (isVideoVolume) Color(0xFFE17055) else Color(0xFF74B9FF),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Volume slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (isVideoVolume) Icons.Default.VolumeOff else Icons.Default.VolumeDown,
                                contentDescription = "Volume Down",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )

                            Slider(
                                value = volume.toFloat(),
                                onValueChange = { onVolumeChange(it.roundToInt()) },
                                valueRange = 0f..200f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = if (isVideoVolume) Color(0xFFE17055) else Color(
                                        0xFF74B9FF
                                    ),
                                    activeTrackColor = if (isVideoVolume) Color(0xFFE17055) else Color(
                                        0xFF74B9FF
                                    ),
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            )

                            Icon(
                                if (isVideoVolume) Icons.Default.RecordVoiceOver else Icons.Default.VolumeUp,
                                contentDescription = "Volume Up",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Add helpful text for video volume
                        if (isVideoVolume) {
                            Text(
                                "Adjust the original video's voice/audio level during playback",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVideoVolume) Color(0xFFE17055) else Color(0xFF74B9FF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Medium)
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ModernButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color else Color.Gray.copy(alpha = 0.3f),
            contentColor = Color.White,
            disabledContainerColor = Color.Gray.copy(alpha = 0.2f),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = text,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AudioWaveform(
    segment: AudioSegment,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    volume: Float = 1f
) {
    Log.i("AudioWaveformSegment", "Different: ${segment.timelineEnd - segment.timelineStart}")

    val waveCountDif = (segment.timelineEnd - segment.timelineStart) / 100
    val waveformData = (0..waveCountDif).map { Random.nextFloat() * 0.8f + 0.1f }

    Log.i("AudioWaveformSegment", "waveformData: ${waveCountDif} then ${waveformData.size}")

    Canvas(modifier = modifier) {
        val barWidth = size.width / waveformData.size
        val maxBarHeight = size.height * 0.9f // Reserve 10% padding from top/bottom

        waveformData.forEachIndexed { index, amplitude ->
            // Calculate the visual amplitude (clamped to container bounds)
            val baseAmplitude = amplitude * volume
            val clampedAmplitude =
                baseAmplitude.coerceAtMost(1.0f) // Never exceed 100% of available space

            // Calculate bar dimensions
            val barHeight = maxBarHeight * clampedAmplitude
            val x = index * barWidth
            val y = (size.height - barHeight) / 2f

            // Color intensity based on volume level
            val colorAlpha = when {
                volume <= 1.0f -> (0.7f + volume * 0.3f).coerceAtMost(1f)
                else -> {
                    // For volumes > 100%, increase color intensity but keep bars within bounds
                    val intensityBoost = ((volume - 1.0f) * 0.3f).coerceAtMost(0.3f)
                    (0.7f + 0.3f + intensityBoost).coerceAtMost(1f)
                }
            }

            // Color changes based on volume level
            val waveColor = when {
                volume <= 1.0f -> Color.White.copy(alpha = colorAlpha)
                volume <= 1.5f -> Color(0xFFFFE066).copy(alpha = colorAlpha) // Light yellow for moderate boost
                else -> Color(0xFFFF6B6B).copy(alpha = colorAlpha) // Light red for high boost
            }

            drawRoundRect(
                color = waveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth * 0.8f, barHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )

            // Add subtle border effect for high volume levels
            if (volume > 1.2f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth * 0.8f, barHeight),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                    style = Stroke(width = 0.5.dp.toPx())
                )
            }
        }

        // Optional: Add a subtle volume indicator overlay for high volumes
        if (volume > 1.0f) {
            val overlayAlpha = ((volume - 1.0f) * 0.1f).coerceAtMost(0.2f)
            drawRect(
                color = Color.Red.copy(alpha = overlayAlpha),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height)
            )
        }
    }
}

@Composable
fun ModernVolumeDialog(
    title: String,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color(0xFF74B9FF),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Volume display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF74B9FF).copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$volume%",
                                color = Color(0xFF74B9FF),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Volume slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.VolumeDown,
                                contentDescription = "Volume Down",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )

                            Slider(
                                value = volume.toFloat(),
                                onValueChange = { onVolumeChange(it.roundToInt()) },
                                valueRange = 0f..200f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF74B9FF),
                                    activeTrackColor = Color(0xFF74B9FF),
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                            )

                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Volume Up",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }


                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF74B9FF)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Medium)
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun MoreOptionsDialog(
    onDismiss: () -> Unit,
    onDuplicateSegment: () -> Unit,
    onMergeSegments: () -> Unit,
    onResetVolume: () -> Unit,
    onExportAudioOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color(0xFF636E72),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "More Options",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MoreOptionItem(
                    icon = Icons.Default.ContentCopy,
                    text = "Duplicate Segment",
                    onClick = {
                        onDuplicateSegment()
                        onDismiss()
                    }
                )

                MoreOptionItem(
                    icon = Icons.Default.MergeType,
                    text = "Merge Adjacent Segments",
                    onClick = {
                        onMergeSegments()
                        onDismiss()
                    }
                )

                MoreOptionItem(
                    icon = Icons.Default.Refresh,
                    text = "Reset Volume",
                    onClick = {
                        onResetVolume()
                        onDismiss()
                    }
                )

                MoreOptionItem(
                    icon = Icons.Default.AudioFile,
                    text = "Export Audio Only",
                    onClick = {
                        onExportAudioOnly()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF636E72))
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun MoreOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                Color(0xFF1A1A1A),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}


private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun AudioEditorScreen(
    videoFile: File,
    audioFile: File,
    onBackToCamera: () -> Unit,
    onExportComplete: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showVideoVolumeDialog by remember { mutableStateOf(false) }
    var videoVolumePercentage by remember { mutableIntStateOf(100) }

    var exportedFile by remember { mutableStateOf<File?>(null) }
    var showExportSuccess by remember { mutableStateOf(false) }


    var timelineState by remember { mutableStateOf(TimelineState()) }
    var editState by remember { mutableStateOf(EditState()) }
    var isPlaying by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var selectedSegmentVolume by remember { mutableIntStateOf(100) }

    // Export state
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }

    val merger = remember { AudioVideoMerger(context) }


    fun performExport() {
        isExporting = true
        exportProgress = 0
        exportedFile = null
        showExportSuccess = false

        scope.launch {
            try {
                // Convert UI segments to AudioSegmentInfo for the merger
                val audioSegmentInfos = editState.segments.map { segment ->
                    AudioVideoMerger.AudioSegmentInfo(
                        startTime = segment.timelineStart,
                        endTime = segment.timelineEnd,
                        originalStartTime = segment.originalStartTime,
                        originalEndTime = segment.originalEndTime,
                        volume = segment.volume
                    )
                }

                // Use the enhanced merge function
                merger.mergeWithAudioSegments(
                    videoFile = videoFile,
                    audioFile = audioFile,
                    audioSegments = audioSegmentInfos,
                    videoVolume = editState.videoVolume,
                    outputFileName = "edited_video_${System.currentTimeMillis()}.mp4",
                    onProgress = { progress, message ->
                        exportProgress = progress
                        Log.d("Export", "Progress: $progress% - $message")
                    },
                    onComplete = { resultFile ->
                        isExporting = false
                        if (resultFile != null) {
                            exportedFile = resultFile
                            showExportSuccess = true
                            exportProgress = 100
                            Log.d("Export", "Export successful: ${resultFile.absolutePath}")
                        } else {
                            exportProgress = 0
                            Log.e("Export", "Export failed - no result file")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("AudioEditorScreen", "Export failed", e)
                isExporting = false
                exportProgress = 0
            }
        }
    }

    // Function to open the exported file
    fun openExportedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Log.e("AudioEditorScreen", "Failed to open file", e)
        }
    }

    // Function to share the exported file
    fun shareExportedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share video"))
        } catch (e: Exception) {
            Log.e("AudioEditorScreen", "Failed to share file", e)
        }
    }

    // Helper function to format file size
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // Players setup
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoFile.toURI().toString()))
            prepare()
        }
    }

    val audioPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioFile.toURI().toString()))
            prepare()
        }
    }

    DisposableEffect(videoPlayer, audioPlayer) {
        onDispose {
            videoPlayer.release()
            audioPlayer.release()
        }
    }

    // States

    fun changeVideoVolume(volumePercentage: Int) {
        val volume = volumePercentage / 100f
        editState = editState.copy(videoVolume = volume)
        videoPlayer.volume = volume
        videoVolumePercentage = volumePercentage
    }

    // Enhanced history management
    fun addToHistory(segments: List<AudioSegment>) {
        val newHistory = editState.history.take(editState.historyIndex + 1) + listOf(segments)
        editState = editState.copy(
            history = newHistory,
            historyIndex = newHistory.size - 1,
            // Ensure selection remains valid or auto-select first segment
            selectedSegmentId = editState.selectedSegmentId?.takeIf { id ->
                segments.any { it.id == id }
            } ?: segments.firstOrNull()?.id
        )
    }

    fun undo() {
        if (editState.canUndo) {
            val previousSegments = editState.history[editState.historyIndex]
            editState = editState.copy(
                segments = previousSegments,
                historyIndex = editState.historyIndex - 1,
                // Auto-select first segment if current selection is invalid
                selectedSegmentId = editState.selectedSegmentId?.takeIf { id ->
                    previousSegments.any { it.id == id }
                } ?: previousSegments.firstOrNull()?.id
            )
        }
    }

    fun redo() {
        if (editState.canRedo) {
            val newIndex = editState.historyIndex + 1
            val nextSegments = editState.history[newIndex]
            editState = editState.copy(
                segments = nextSegments,
                historyIndex = newIndex,
                // Auto-select first segment if current selection is invalid
                selectedSegmentId = editState.selectedSegmentId?.takeIf { id ->
                    nextSegments.any { it.id == id }
                } ?: nextSegments.firstOrNull()?.id
            )
        }
    }

    // Enhanced segment deletion with auto-selection of next segment
    fun deleteSegment(segmentId: String) {
        if (editState.segments.size <= 1) {
            // Don't delete if it's the last segment
            return
        }

        addToHistory(editState.segments)
        val remainingSegments = editState.segments.filter { it.id != segmentId }

        // Find the next segment to select
        val currentIndex = editState.segments.indexOfFirst { it.id == segmentId }
        val nextSelectedId = when {
            currentIndex < remainingSegments.size -> remainingSegments[currentIndex].id
            remainingSegments.isNotEmpty() -> remainingSegments.last().id
            else -> null
        }

        editState = editState.copy(
            segments = remainingSegments,
            selectedSegmentId = nextSelectedId
        )
    }

    // Enhanced split function with smart selection
    fun splitSegment(segmentId: String, splitTime: Long) {
        val segment = editState.segments.find { it.id == segmentId } ?: return
        if (splitTime <= segment.timelineStart || splitTime >= segment.timelineEnd) return

        addToHistory(editState.segments)

        val splitOffset = splitTime - segment.timelineStart
        val originalSplitTime = segment.originalStartTime + splitOffset

        val segment1 = segment.copy(
            id = "${segmentId}_1",
            originalEndTime = originalSplitTime
        )

        val segment2 = AudioSegment(
            id = "${segmentId}_2",
            originalStartTime = originalSplitTime,
            originalEndTime = segment.originalEndTime,
            timelineStart = splitTime,
            volume = segment.volume
        )

        val newSegments = editState.segments.map {
            if (it.id == segmentId) segment1 else it
        } + segment2

        editState = editState.copy(
            segments = newSegments.sortedBy { it.timelineStart },
            // Keep the first part selected after split
            selectedSegmentId = segment1.id
        )
    }

    // Initialize with video duration and create initial audio segment
    LaunchedEffect(videoPlayer, audioPlayer) {
        while (true) {
            val videoDuration = videoPlayer.duration
            val audioDuration = audioPlayer.duration

            if (videoDuration > 0L && timelineState.duration == 0L) {
                timelineState = timelineState.copy(duration = videoDuration)

                if (audioDuration > 0L && editState.segments.isEmpty()) {
                    val initialSegment = AudioSegment(
                        id = "segment_0",
                        originalStartTime = 0L,
                        originalEndTime = audioDuration,
                        timelineStart = 0L,
                        volume = 1.0f
                    )
                    val initialSegments = listOf(initialSegment)
                    editState = editState.copy(
                        segments = initialSegments,
                        history = listOf(initialSegments),
                        historyIndex = 0
                    )
                }
            }

            // Update position during playback
            if (isPlaying) {
                val newPosition = videoPlayer.currentPosition
                if (newPosition != timelineState.currentPosition && newPosition <= timelineState.duration) {
                    timelineState = timelineState.copy(currentPosition = newPosition)
                }
            }

            delay(100)
        }
    }

    // Sync players with timeline position
    fun syncPlayersToPosition(position: Long) {
        videoPlayer.seekTo(position.coerceAtMost(timelineState.duration))

        val activeSegment = editState.segments.find { segment ->
            position >= segment.timelineStart && position < segment.timelineEnd
        }

        if (activeSegment != null) {
            val segmentOffset = position - activeSegment.timelineStart
            val audioPosition = activeSegment.originalStartTime + segmentOffset
            audioPlayer.seekTo(audioPosition)
            audioPlayer.volume = activeSegment.volume
        } else {
            audioPlayer.pause()
        }

        timelineState = timelineState.copy(currentPosition = position)
    }

    // Handle playback
    LaunchedEffect(isPlaying, timelineState.currentPosition, editState.videoVolume) {
        if (isPlaying) {
            val position = timelineState.currentPosition

            if (position < timelineState.duration) {
                if (!videoPlayer.isPlaying) {
                    videoPlayer.volume = editState.videoVolume // Apply video volume
                    videoPlayer.play()
                }
            } else {
                videoPlayer.pause()
            }

            val activeSegment = editState.segments.find { segment ->
                position >= segment.timelineStart && position < segment.timelineEnd
            }

            if (activeSegment != null) {
                if (!audioPlayer.isPlaying) {
                    val segmentOffset = position - activeSegment.timelineStart
                    val audioPosition = activeSegment.originalStartTime + segmentOffset
                    audioPlayer.seekTo(audioPosition)
                    audioPlayer.volume = activeSegment.volume
                    audioPlayer.play()
                }
            } else {
                audioPlayer.pause()
            }
        } else {
            videoPlayer.pause()
            audioPlayer.pause()
        }
    }


    fun moveSegment(segmentId: String, newTimelineStart: Long) {
        val segmentToMove = editState.segments.find { it.id == segmentId } ?: return
        val segmentDuration = segmentToMove.originalDuration

        // Calculate the valid range for this segment
        val otherSegments = editState.segments.filter { it.id != segmentId }

        // Find the maximum start position (limited by segments that come after)
        val maxStart = otherSegments
            .filter { it.timelineStart > segmentToMove.timelineStart }
            .minOfOrNull { it.timelineStart }
            ?.let { it - segmentDuration }
            ?: (timelineState.duration - segmentDuration)

        // Find the minimum start position (limited by segments that come before)
        val minStart = otherSegments
            .filter { it.timelineEnd <= segmentToMove.timelineStart }
            .maxOfOrNull { it.timelineEnd }
            ?: 0L

        // Constrain the new position to prevent overlaps
        val constrainedStart = newTimelineStart
            .coerceAtLeast(minStart)
            .coerceAtMost(maxStart)
            .coerceAtLeast(0L)
            .coerceAtMost(timelineState.duration - segmentDuration)

        val newSegments = editState.segments.map { segment ->
            if (segment.id == segmentId) {
                segment.copy(timelineStart = constrainedStart)
            } else segment
        }
        editState = editState.copy(segments = newSegments.sortedBy { it.timelineStart })
    }

    fun changeSegmentVolume(segmentId: String, volume: Float) {
        addToHistory(editState.segments)
        val newSegments = editState.segments.map { segment ->
            if (segment.id == segmentId) {
                segment.copy(volume = volume)
            } else segment
        }
        editState = editState.copy(segments = newSegments)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Modern top control bar
        ModernTopControlBar(
            onBackClick = onBackToCamera,
            isExporting = isExporting,
            onExport = { performExport() }, // Updated to use new function
            canUndo = editState.canUndo,
            canRedo = editState.canRedo,
            onUndo = ::undo,
            onRedo = ::redo
        )

        // Video preview with merged timeline and audio segments
        VideoPreviewWithTimeline(
            videoFile = videoFile,
            videoPlayer = videoPlayer,
            currentPosition = timelineState.currentPosition,
            totalDuration = timelineState.duration,
            onSeek = { position -> syncPlayersToPosition(position) },
            timelineState = timelineState,
            editState = editState,
            onMoveSegment = ::moveSegment,
            onSelectSegment = { segmentId ->
                editState = editState.copy(selectedSegmentId = segmentId)
            },
            videoVolume = editState.videoVolume, // Pass video volume
            onVideoVolumeClick = { showVideoVolumeDialog = true }, // Add video volume handler
            isPlaying = isPlaying,
            onPlayPauseClick = { isPlaying = !isPlaying },
            syncPlayersToPosition = ::syncPlayersToPosition
        )

        // Modern playback controls with zoom controls


        // Simplified audio editing controls
        ModernAudioEditingControls(
            selectedSegmentId = editState.effectiveSelectedSegmentId,

            onSplit = {
                editState.effectiveSelectedSegmentId?.let { segmentId ->
                    splitSegment(segmentId, timelineState.currentPosition)
                }
            },
            onDelete = {
                editState.effectiveSelectedSegmentId?.let(::deleteSegment)
            },
            onVolumeClick = { showVolumeDialog = true },

            onZoomIn = {
                timelineState =
                    timelineState.copy(zoom = (timelineState.zoom * 1.4f).coerceAtMost(5f))
            },
            onZoomOut = {
                timelineState =
                    timelineState.copy(zoom = (timelineState.zoom * 0.7f).coerceAtLeast(0.3f))
            }
        )

        if (showVideoVolumeDialog) {
            ModernVolumeDialog(
                title = "Video Voice Level",
                volume = videoVolumePercentage,
                isVideoVolume = true, // Mark as video volume dialog
                onVolumeChange = { newVolume ->
                    changeVideoVolume(newVolume)
                },
                onDismiss = { showVideoVolumeDialog = false }
            )
        }
    }

    // Modern Volume dialog for selected segment
    if (showVolumeDialog && editState.effectiveSelectedSegmentId != null) {
        ModernVolumeDialog(
            title = "Segment Volume",
            volume = selectedSegmentVolume,
            onVolumeChange = { newVolume ->
                selectedSegmentVolume = newVolume
                editState.effectiveSelectedSegmentId?.let { segmentId ->
                    changeSegmentVolume(segmentId, newVolume / 100f)
                }
            },
            onDismiss = { showVolumeDialog = false }
        )
    }

    // More options dialog
    if (showMoreOptions) {
        MoreOptionsDialog(
            onDismiss = { showMoreOptions = false },
            onDuplicateSegment = {
                editState.selectedSegmentId?.let { segmentId ->
                    val segment = editState.segments.find { it.id == segmentId }
                    if (segment != null) {
                        addToHistory(editState.segments)
                        val duplicatedSegment = segment.copy(
                            id = "${segmentId}_dup",
                            timelineStart = segment.timelineEnd
                        )
                        editState = editState.copy(
                            segments = (editState.segments + duplicatedSegment).sortedBy { it.timelineStart }
                        )
                    }
                }
            },
            onMergeSegments = {
                // Merge adjacent segments logic
                addToHistory(editState.segments)
                // Implementation would go here
            },
            onResetVolume = {
                editState.selectedSegmentId?.let { segmentId ->
                    changeSegmentVolume(segmentId, 1.0f)
                    selectedSegmentVolume = 100
                }
            },
            onExportAudioOnly = {
                // Export audio only logic
            }
        )
    }


    if (isExporting) {
        ExportProgressDialog(
            progress = exportProgress,
            isExporting = isExporting,
            onCancel = {
                // Cancel export if needed
                isExporting = false
                exportProgress = 0
            }
        )
    }

    // Export success dialog
    if (showExportSuccess && exportedFile != null) {
        ExportSuccessDialog(
            exportedFile = exportedFile!!,
            onDismiss = {
                showExportSuccess = false
                exportedFile = null
            },
            onOpenFile = {
                openExportedFile(exportedFile!!)
                showExportSuccess = false
                exportedFile = null
            },
            onShareFile = {
                shareExportedFile(exportedFile!!)
                showExportSuccess = false
                exportedFile = null
            },
            formatFileSize = ::formatFileSize
        )
    }
}



@Composable
fun ExportProgressDialog(
    progress: Int,
    isExporting: Boolean,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { }, // Prevent dismissal during export
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF00D4AA),
                    strokeWidth = 3.dp
                )
                Text(
                    "Exporting Video",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress percentage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF00D4AA).copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$progress%",
                        color = Color(0xFF00D4AA),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF00D4AA),
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )

                Text(
                    "Please wait while your video is being processed...",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp)
    )
}

// NEW: Export Success Dialog Composable
@Composable
fun ExportSuccessDialog(
    exportedFile: File,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    formatFileSize: (Long) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00D4AA),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Export Successful!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // File info
                        Text(
                            "File Details",
                            color = Color(0xFF00D4AA),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Name: ${exportedFile.name}",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Text(
                            "Size: ${formatFileSize(exportedFile.length())}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        // Clickable file path
                        Text(
                            "Location:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFile() }
                                .background(
                                    Color(0xFF2D2D2D),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Open location",
                                tint = Color(0xFF74B9FF),
                                modifier = Modifier.size(16.dp)
                            )

                            Text(
                                exportedFile.parent ?: "Unknown location",
                                color = Color(0xFF74B9FF),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open file button
                    Button(
                        onClick = onOpenFile,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF74B9FF)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp)
                    }

                    // Share button
                    Button(
                        onClick = onShareFile,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D4AA)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("Done")
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(16.dp)
    )
}


@Composable
fun ModernTopControlBar(
    onBackClick: () -> Unit,
    isExporting: Boolean,
    onExport: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    var showMoreOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left section - Back button
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color(0xFF2D2D2D),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center section - Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Audio Editor",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Edit • Mix • Export",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // Right section - More options button
            Box {
                IconButton(
                    onClick = { showMoreOptions = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color(0xFF2D2D2D),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = showMoreOptions,
                    onDismissRequest = { showMoreOptions = false },
                    modifier = Modifier
                        .background(
                            Color(0xFF2D2D2D),
                            RoundedCornerShape(12.dp)
                        )
                        .width(200.dp),
                    offset = DpOffset(0.dp, 8.dp)
                ) {
                    // Undo option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) Color.White else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Undo",
                                    color = if (canUndo) Color.White else Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        onClick = {
                            if (canUndo) {
                                onUndo()
                                showMoreOptions = false
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier
                            .background(
                                if (canUndo) Color.Transparent else Color.Gray.copy(alpha = 0.1f)
                            )
                    )

                    // Redo option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) Color.White else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Redo",
                                    color = if (canRedo) Color.White else Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        onClick = {
                            if (canRedo) {
                                onRedo()
                                showMoreOptions = false
                            }
                        },
                        enabled = canRedo,
                        modifier = Modifier
                            .background(
                                if (canRedo) Color.Transparent else Color.Gray.copy(alpha = 0.1f)
                            )
                    )

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Gray.copy(alpha = 0.3f)
                    )

                    // Export option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF007AFF),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.FileUpload,
                                        contentDescription = "Export",
                                        tint = if (!isExporting) Color(0xFF007AFF) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    if (isExporting) "Exporting..." else "Export",
                                    color = if (!isExporting) Color(0xFF007AFF) else Color.Gray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        onClick = {
                            if (!isExporting) {
                                onExport()
                                showMoreOptions = false
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier
                            .background(
                                if (!isExporting) Color.Transparent else Color.Gray.copy(alpha = 0.1f)
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewWithTimeline(
    videoFile: File,
    videoPlayer: ExoPlayer,
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    timelineState: TimelineState,
    editState: EditState,
    onMoveSegment: (String, Long) -> Unit,
    onSelectSegment: (String) -> Unit,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    syncPlayersToPosition: (Long) -> Unit,
    videoVolume: Float, // Add video volume parameter
    onVideoVolumeClick: () -> Unit, // Add video volume click handler
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Larger Video preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp), // Fixed larger height
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // NEW: Video voice adjustment button
                IconButton(
                    onClick = onVideoVolumeClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd) // Positions in top-right corner
                        .padding(12.dp) // Adds margin from edges
                        .size(30.dp) // Button size
                        .background(
                            // Color changes based on video volume level
                            Color(0xFF673AB7),
                            CircleShape
                        )
                ) {
                    // Icon changes based on video volume level
                    Icon(
                        when {
                            videoVolume == 0f -> Icons.Default.VolumeUp
                            videoVolume <= 0.5f -> Icons.Default.VolumeDown
                            else -> Icons.Default.RecordVoiceOver
                        },
                        contentDescription = "Video Voice",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Merged timeline with audio segments
        Column {
            // Time display with inline playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current time display
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        formatTime(currentPosition),
                        color = Color(0xFF00D4AA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Inline playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                            syncPlayersToPosition(newPosition)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2D2D2D), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Back 10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF007AFF),
                                        Color(0xFF0056CC)
                                    )
                                ),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val newPosition = (currentPosition + 10000).coerceAtMost(totalDuration)
                            syncPlayersToPosition(newPosition)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2D2D2D), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Total duration display
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        formatTime(totalDuration),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            IntegratedTimelineView(
                timelineState = timelineState,
                editState = editState,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                onSeek = onSeek,
                onMoveSegment = onMoveSegment,
                onSelectSegment = onSelectSegment
            )
        }
    }
}

@Composable
fun ModernPlaybackControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2D2D2D), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Back 10s",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF007AFF),
                                    Color(0xFF0056CC)
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF2D2D2D), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}


// Enhanced AudioSegmentViewSmooth to work better with zoomed timeline
@Composable
fun IntegratedTimelineView(
    timelineState: TimelineState,
    editState: EditState,
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    onMoveSegment: (String, Long) -> Unit,
    onSelectSegment: (String) -> Unit
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    Column {
        // Main timeline container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Timeline height
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2A2A2A),
                            Color(0xFF1E1E1E)
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
        ) {
            val baseTimelineWidth = maxWidth
            val expandedTimelineWidth = baseTimelineWidth * timelineState.zoom
            val timelineWidthPx = with(density) { expandedTimelineWidth.toPx() }

            // Timeline container with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Timeline content with expanded width
                Box(
                    modifier = Modifier
                        .width(expandedTimelineWidth)
                        .fillMaxHeight()
                ) {

                    TimeRuler(
                        totalDuration = totalDuration,
                        timelineWidth = expandedTimelineWidth,
                        zoom = timelineState.zoom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .align(Alignment.TopCenter)
                    )

                    // Audio segments in the middle section with proper event handling
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .offset(y = 30.dp) // Position segments in middle
                    ) {
                        editState.segments.forEach { segment ->
                            if (totalDuration > 0) {
                                val startRatio = segment.timelineStart.toFloat() / totalDuration
                                val endRatio = segment.timelineEnd.toFloat() / totalDuration

                                val startOffset = expandedTimelineWidth * startRatio
                                val segmentWidth = expandedTimelineWidth * (endRatio - startRatio)

                                if (segmentWidth > 4.dp) {
                                    AudioSegmentViewSmooth(
                                        segment = segment,
                                        isSelected = segment.id == editState.effectiveSelectedSegmentId, // Use effective selection
                                        timelineState = timelineState,
                                        segmentWidth = segmentWidth,
                                        timelineWidthPx = timelineWidthPx,
                                        onMoveSegment = onMoveSegment,
                                        onSelectSegment = onSelectSegment,
                                        scrollState = scrollState,
                                        onScrollTimeline = { },
                                        modifier = Modifier
                                            .width(segmentWidth)
                                            .fillMaxHeight()
                                            .offset(x = startOffset)
                                            .padding(vertical = 8.dp)
                                            .zIndex(if (segment.id == editState.effectiveSelectedSegmentId) 2f else 1f)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive timeline scrubber overlay (only in non-segment areas)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp) // Top portion only for timeline scrubbing
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val ratio = offset.x / size.width
                                    val newPosition = (ratio * totalDuration)
                                        .toLong()
                                        .coerceAtLeast(0L)
                                        .coerceAtMost(totalDuration)
                                    onSeek(newPosition)
                                }
                            }
                    )

                    if (totalDuration > 0) {
                        val playheadRatio = currentPosition.toFloat() / totalDuration
                        val playheadOffset = expandedTimelineWidth * playheadRatio

                        // State to track dragging
                        var isDragging by remember { mutableStateOf(false) }
                        var dragStartX by remember { mutableFloatStateOf(0f) }
                        var dragStartPosition by remember { mutableLongStateOf(0L) }

                        // Combined playhead rendering in a single Canvas
                        Canvas(
                            modifier = Modifier
                                .width(expandedTimelineWidth)
                                .height(40.dp)
                                .zIndex(10f)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            isDragging = true
                                            dragStartX = offset.x

                                            // Calculate position where user touched, not current playhead position
                                            val touchRatio = offset.x / size.width.toFloat()
                                            val touchPosition =
                                                (touchRatio * totalDuration)
                                                    .toLong()
                                                    .coerceAtLeast(0L)
                                                    .coerceAtMost(totalDuration)

                                            // Set both drag start and immediately seek to touch position
                                            dragStartPosition = touchPosition
                                            onSeek(touchPosition)
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (isDragging) {
                                                // Calculate total drag distance from the touch start point
                                                val totalDragX = change.position.x - dragStartX

                                                // Convert drag distance to time offset
                                                val dragRatio = totalDragX / size.width.toFloat()
                                                val timeOffset =
                                                    (dragRatio * totalDuration).toLong()

                                                // Calculate new position based on where user initially touched
                                                val newPosition = (dragStartPosition + timeOffset)
                                                    .coerceAtLeast(0L)
                                                    .coerceAtMost(totalDuration)

                                                onSeek(newPosition)
                                            }
                                        }
                                    )
                                }
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val playheadX = with(density) { playheadOffset.toPx() }

                            // Draw the playhead line
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFF4444),
                                        Color(0xFFFF0000),
                                        Color(0xFFFF4444)
                                    )
                                ),
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, canvasHeight),
                                strokeWidth = 2.dp.toPx()
                            )

                            // Draw the triangle thumb at the top of the line
                            val triangleSize = 16.dp.toPx()
                            val triangleCenterX = playheadX
                            val triangleTop = 0f
                            val triangleBottom = triangleSize * 0.8f

                            // Create triangle path
                            val trianglePath = Path().apply {
                                // Start at top-left
                                moveTo(triangleCenterX - triangleSize * 0.4f, triangleTop)
                                // Line to top-right
                                lineTo(triangleCenterX + triangleSize * 0.4f, triangleTop)
                                // Line to bottom center (point of triangle)
                                lineTo(triangleCenterX, triangleBottom)
                                // Close path back to start
                                close()
                            }

                            // Draw the main triangle with gradient effect
                            drawPath(
                                path = trianglePath,
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFF4444),
                                        Color(0xFFCC0000)
                                    ),
                                    center = Offset(triangleCenterX, triangleSize * 0.3f),
                                    radius = triangleSize * 0.6f
                                )
                            )

                            // Add highlight for 3D effect
                            val highlightPath = Path().apply {
                                moveTo(triangleCenterX - triangleSize * 0.3f, triangleSize * 0.1f)
                                lineTo(triangleCenterX + triangleSize * 0.3f, triangleSize * 0.1f)
                                lineTo(triangleCenterX, triangleSize * 0.4f)
                                close()
                            }

                            drawPath(
                                path = highlightPath,
                                color = Color.White.copy(alpha = 0.3f)
                            )

                            // Optional: Draw a subtle glow effect during dragging
                            if (isDragging) {
                                drawCircle(
                                    color = Color(0xFFFF4444).copy(alpha = 0.3f),
                                    radius = 20.dp.toPx(),
                                    center = Offset(triangleCenterX, triangleSize * 0.5f)
                                )
                            }
                        }

                        // Auto-scroll to keep playhead visible - only when not dragging
                        LaunchedEffect(currentPosition, timelineState.zoom) {
                            if (!isDragging) {
                                val viewportWidth = with(density) { baseTimelineWidth.toPx() }
                                val playheadX = with(density) { playheadOffset.toPx() }
                                val currentScroll = scrollState.value.toFloat()
                                val visibleRange = currentScroll..(currentScroll + viewportWidth)

                                if (playheadX !in visibleRange) {
                                    val targetScroll =
                                        (playheadX - viewportWidth / 2).coerceAtLeast(0f)
                                    scrollState.animateScrollTo(targetScroll.toInt())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Horizontal scrollbar indicator - positioned OUTSIDE and BELOW the timeline box
        if (timelineState.zoom > 1f) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp) // Add some spacing between timeline and scrollbar
            ) {
                val baseTimelineWidth = maxWidth
                val scrollbarHeight = 5.dp
                val scrollbarWidth = (baseTimelineWidth / timelineState.zoom).coerceAtLeast(24.dp)
                val scrollbarOffset =
                    (scrollState.value.dp / timelineState.zoom).coerceAtMost(baseTimelineWidth - scrollbarWidth)

                val scope = rememberCoroutineScope()

                // Draggable scrollbar
                Box(
                    modifier = Modifier
                        .offset(x = scrollbarOffset)
                        .width(scrollbarWidth)
                        .height(scrollbarHeight)
                        .background(
                            Color(0xFF00D4AA).copy(alpha = 0.9f),
                            RoundedCornerShape(scrollbarHeight / 2)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF00B89A).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(scrollbarHeight / 2)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Optional: Add visual feedback for drag start
                                },
                                onDragEnd = {
                                    // Optional: Add visual feedback for drag end
                                },
                                onDrag = { change, dragAmount ->
                                    scope.launch {
                                        // Calculate the new scroll position based on drag
                                        val dragRatio = dragAmount.x / size.width.toFloat()
                                        val maxScrollValue = scrollState.maxValue
                                        val scrollDelta =
                                            (dragRatio * maxScrollValue * timelineState.zoom).toInt()

                                        val newScrollValue = (scrollState.value + scrollDelta)
                                            .coerceAtLeast(0)
                                            .coerceAtMost(maxScrollValue)

                                        scrollState.scrollTo(newScrollValue)
                                    }
                                }
                            )
                        }
                )
                {
                    // Empty content - styling is handled by the modifier
                }
            }
        }
    }
}

// Enhanced AudioSegmentViewSmooth with long press for drag and regular drag for seek
// Enhanced AudioSegmentViewSmooth with long press for drag and regular drag for scroll
@Composable
fun AudioSegmentViewSmooth(
    segment: AudioSegment,
    isSelected: Boolean,
    timelineState: TimelineState,
    segmentWidth: Dp,
    timelineWidthPx: Float,
    onMoveSegment: (String, Long) -> Unit,
    onSelectSegment: (String) -> Unit,
    scrollState: ScrollState, // Changed from currentPosition
    onScrollTimeline: (Float) -> Unit, // Changed from onSeek
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressTriggered by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableLongStateOf(0L) }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var scrollStartPosition by remember { mutableFloatStateOf(0f) }
    var accumulatedScrollDrag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00D4AA).copy(alpha = if (isDragging) 1.0f else 0.9f),
                            Color(0xFF00B894).copy(alpha = if (isDragging) 1.0f else 0.9f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00B894).copy(alpha = 0.7f),
                            Color(0xFF00D4AA).copy(alpha = 0.5f)
                        )
                    )
                },
                RoundedCornerShape(8.dp)
            )
            .pointerInput(segment.id) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (isLongPressTriggered) {
                            // Long press mode: start segment dragging
                            isDragging = true
                            dragStartPosition = segment.timelineStart
                            accumulatedDrag = 0f
                            onSelectSegment(segment.id)
                        } else {
                            // No long press: start timeline scrolling
                            isDragging = true
                            scrollStartPosition = scrollState.value.toFloat()
                            accumulatedScrollDrag = 0f
                            // No immediate action needed for scrolling
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        isLongPressTriggered = false
                        accumulatedDrag = 0f
                        accumulatedScrollDrag = 0f
                        scrollStartPosition = 0f
                    },
                    onDrag = { change, delta ->
                        if (isDragging) {
                            if (isLongPressTriggered) {
                                // Long press mode: move segment
                                accumulatedDrag += delta.x
                                val dragRatio = accumulatedDrag / timelineWidthPx
                                val timeOffset = (dragRatio * timelineState.duration).toLong()

                                val newPosition = (dragStartPosition + timeOffset)
                                    .coerceAtLeast(0L)
                                    .coerceAtMost(timelineState.duration - segment.originalDuration)

                                onMoveSegment(segment.id, newPosition)
                            } else {
                                // No long press: scroll timeline based on accumulated drag
                                accumulatedScrollDrag += delta.x

                                // Calculate scroll offset (invert direction for natural scrolling)
                                val scrollDelta = -accumulatedScrollDrag
                                val newScrollPosition = (scrollStartPosition + scrollDelta)
                                    .coerceAtLeast(0f)
                                    .coerceAtMost(scrollState.maxValue.toFloat())

                                scope.launch {
                                    scrollState.scrollTo(newScrollPosition.toInt())
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(segment.id) {
                detectTapGestures(
                    onLongPress = {
                        // Trigger long press for drag mode
                        isLongPressTriggered = true
                        // Add haptic feedback or visual indication here if needed
                    },
                    onTap = {
                        // Regular tap for selection (only if not in drag mode)
                        if (!isDragging && !isLongPressTriggered) {
                            onSelectSegment(segment.id)
                        }
                    }
                )
            }
    ) {
        // Visual feedback during drag
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isLongPressTriggered) {
                            // Yellow tint for segment moving
                            Color.Yellow.copy(alpha = 0.2f)
                        } else {
                            // Blue tint for timeline scrolling
                            Color.Blue.copy(alpha = 0.2f)
                        },
                        RoundedCornerShape(8.dp)
                    )
            )
        }

        // Long press indicator (when long pressed but not yet dragging)
        if (isLongPressTriggered && !isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Yellow.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
            )

            // Pulsing border effect for long press
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = Color.Yellow.copy(alpha = 0.8f),
                            style = Stroke(width = 3.dp.toPx()),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                    }
            )
        }

        // Enhanced waveform that adapts to zoom level
        AudioWaveform(
            segment = segment,
            modifier = Modifier.fillMaxSize(),
            zoom = timelineState.zoom,
            volume = segment.volume
        )

        // Enhanced drag handles that are more visible when zoomed and in drag mode
        if (isSelected && isLongPressTriggered) {
            val handleWidth = (6.dp * timelineState.zoom.coerceAtMost(2f))

            // Left drag handle with enhanced visibility
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(handleWidth)
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color.Gray.copy(alpha = 0.8f)
                            )
                        ),
                        RoundedCornerShape(handleWidth / 2)
                    )
                    .drawWithContent {
                        drawContent()
                        // Add grip lines
                        repeat(3) { index ->
                            val y = center.y + (index - 1) * 4.dp.toPx()
                            drawLine(
                                color = Color.Gray,
                                start = Offset(center.x - 1.dp.toPx(), y),
                                end = Offset(center.x + 1.dp.toPx(), y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
            )

            // Right drag handle with enhanced visibility
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(handleWidth)
                    .height(32.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color.Gray.copy(alpha = 0.8f)
                            )
                        ),
                        RoundedCornerShape(handleWidth / 2)
                    )
                    .drawWithContent {
                        drawContent()
                        // Add grip lines
                        repeat(3) { index ->
                            val y = center.y + (index - 1) * 4.dp.toPx()
                            drawLine(
                                color = Color.Gray,
                                start = Offset(center.x - 1.dp.toPx(), y),
                                end = Offset(center.x + 1.dp.toPx(), y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
            )
        } else if (isSelected) {
            // Show smaller handles when selected but not in drag mode
            val handleWidth = (3.dp * timelineState.zoom.coerceAtMost(2f))

            // Left drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(handleWidth)
                    .height(20.dp)
                    .background(
                        Color.White.copy(alpha = 0.7f),
                        RoundedCornerShape(handleWidth / 2)
                    )
            )

            // Right drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(handleWidth)
                    .height(20.dp)
                    .background(
                        Color.White.copy(alpha = 0.7f),
                        RoundedCornerShape(handleWidth / 2)
                    )
            )
        }

        // Visual indicator for drag mode in top corner
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(12.dp)
                    .background(
                        if (isLongPressTriggered) {
                            Color.Yellow // Move mode
                        } else {
                            Color.Blue // Scroll mode
                        },
                        CircleShape
                    )
            )
        }
    }
}// Data class to hold time interval information

data class TimeIntervals(
    val major: Long,  // Major intervals in milliseconds
    val minor: Long   // Minor intervals in milliseconds
)

// Enhanced TimeRuler with better design and consistent text sizing
@Composable
fun TimeRuler(
    totalDuration: Long,
    timelineWidth: Dp,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .width(timelineWidth)
            .height(30.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate appropriate time intervals based on zoom
        val timeIntervals = calculateTimeIntervals(totalDuration, zoom, canvasWidth)
        val majorInterval = timeIntervals.major
        val minorInterval = timeIntervals.minor

        // Fixed text size that doesn't scale with zoom
        val baseTextSize = 9.sp.toPx()

        // Fixed opacity values - consistent and non-distracting
        val majorAlpha = 0.5f
        val minorAlpha = 0.3f
        val microAlpha = 0.2f

        // Fixed tick mark heights - always consistent
        val majorTickHeight = canvasHeight * 0.4f
        val minorTickHeight = canvasHeight * 0.3f
        val microTickHeight = canvasHeight * 0.2f

        // Draw major time markers (with labels)
        var currentTime = 0L
        while (currentTime <= totalDuration) {
            val position = (currentTime.toFloat() / totalDuration) * canvasWidth

            // Draw major tick mark - consistent thickness
            val majorStrokeWidth = 1.5.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = majorAlpha),
                start = Offset(position, canvasHeight - majorTickHeight),
                end = Offset(position, canvasHeight),
                strokeWidth = majorStrokeWidth
            )

            // Draw time label - always show labels for seconds
            val timeText = formatTimeForRuler(currentTime)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = (255 * 0.4f).toInt() // Fixed alpha for text
                    textSize = baseTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    // Make text thinner
                    strokeWidth = 0.5f
                    style = android.graphics.Paint.Style.FILL_AND_STROKE
                }

                // Always draw text with slight offset from top
                drawText(
                    timeText,
                    position,
                    canvasHeight - majorTickHeight - 2.dp.toPx(),
                    paint
                )
            }

            currentTime += majorInterval
        }

        // Draw minor time markers (no labels, just tick marks)
        if (zoom > 1.2f && minorInterval > 0) {
            currentTime = 0L
            val minorStrokeWidth = 1.dp.toPx() // Fixed thickness

            while (currentTime <= totalDuration) {
                // Skip positions that already have major markers
                if (currentTime % majorInterval != 0L) {
                    val position = (currentTime.toFloat() / totalDuration) * canvasWidth

                    // Draw minor tick mark
                    drawLine(
                        color = Color.White.copy(alpha = minorAlpha),
                        start = Offset(position, canvasHeight - minorTickHeight),
                        end = Offset(position, canvasHeight),
                        strokeWidth = minorStrokeWidth
                    )
                }

                currentTime += minorInterval
            }
        }

        // Draw micro subdivisions when heavily zoomed
        if (zoom > 2.5f && minorInterval > 200) {
            val microInterval = minorInterval / 5
            currentTime = 0L
            val microStrokeWidth = 0.8.dp.toPx() // Fixed thickness

            while (currentTime <= totalDuration) {
                // Skip positions that already have major or minor markers
                if (currentTime % majorInterval != 0L && currentTime % minorInterval != 0L) {
                    val position = (currentTime.toFloat() / totalDuration) * canvasWidth

                    // Draw micro tick mark
                    drawLine(
                        color = Color.White.copy(alpha = microAlpha),
                        start = Offset(position, canvasHeight - microTickHeight),
                        end = Offset(position, canvasHeight),
                        strokeWidth = microStrokeWidth
                    )
                }

                currentTime += microInterval
            }
        }

        // Draw baseline - consistent appearance
        val baselineAlpha = 0.2f
        val baselineStrokeWidth = 0.8.dp.toPx()

        drawLine(
            color = Color.White.copy(alpha = baselineAlpha),
            start = Offset(0f, canvasHeight),
            end = Offset(canvasWidth, canvasHeight),
            strokeWidth = baselineStrokeWidth
        )

        // Optional: Draw a subtle gradient background for better contrast
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.1f),
                    Color.Black.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = canvasHeight * 0.6f
            ),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight * 0.6f)
        )
    }
}

// Enhanced time interval calculation - focus on seconds
fun calculateTimeIntervals(totalDuration: Long, zoom: Float, canvasWidth: Float): TimeIntervals {
    // Adjust target spacing based on zoom
    val baseTargetPixels = 50f
    val zoomAdjustedTarget = baseTargetPixels / zoom.coerceAtLeast(0.5f)
    val targetPixelsPerMajor = zoomAdjustedTarget.coerceIn(30f, 100f)

    val effectiveWidth = canvasWidth
    val targetMajorCount = (effectiveWidth / targetPixelsPerMajor).toInt().coerceAtLeast(3)

    val roughMajorInterval = totalDuration / targetMajorCount

    // Focus on whole seconds for major intervals
    val majorInterval = when {
        roughMajorInterval <= 500 -> 1000L      // 1 second
        roughMajorInterval <= 1500 -> 2000L     // 2 seconds
        roughMajorInterval <= 2500 -> 3000L     // 3 seconds
        roughMajorInterval <= 4000 -> 5000L     // 5 seconds
        roughMajorInterval <= 7500 -> 10000L    // 10 seconds
        roughMajorInterval <= 12500 -> 15000L   // 15 seconds
        roughMajorInterval <= 25000 -> 30000L   // 30 seconds
        roughMajorInterval <= 45000 -> 60000L   // 1 minute
        roughMajorInterval <= 90000 -> 120000L  // 2 minutes
        roughMajorInterval <= 225000 -> 300000L // 5 minutes
        else -> ((roughMajorInterval / 300000) * 300000) // Round to 5-minute intervals
    }

    // Minor intervals - subdivisions of major intervals
    val minorInterval = when {
        majorInterval <= 1000 -> 200L    // 0.2 seconds (5 divisions)
        majorInterval <= 2000 -> 400L    // 0.4 seconds (5 divisions)
        majorInterval <= 3000 -> 500L    // 0.5 seconds (6 divisions)
        majorInterval <= 5000 -> 1000L   // 1 second (5 divisions)
        majorInterval <= 10000 -> 2000L  // 2 seconds (5 divisions)
        majorInterval <= 15000 -> 3000L  // 3 seconds (5 divisions)
        majorInterval <= 30000 -> 5000L  // 5 seconds (6 divisions)
        majorInterval <= 60000 -> 10000L // 10 seconds (6 divisions)
        else -> majorInterval / 5        // 5 divisions for longer durations
    }

    return TimeIntervals(majorInterval, minorInterval)
}

// Enhanced time formatting - show only seconds
fun formatTimeForRuler(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return when {
        timeMs == 0L -> "0s"
        minutes > 0 -> "${minutes}m${seconds}s".takeIf { seconds > 0 } ?: "${minutes}m"
        else -> "${seconds}s"
    }
}