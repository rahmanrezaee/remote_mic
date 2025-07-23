package com.example.remote_mic.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    videoFile: File,
    audioFile: File,
    onBackToCamera: () -> Unit,
    onExportComplete: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Video player setup
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoFile.toURI().toString()))
            prepare()
        }
    }

    // Audio player setup
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

    // Timeline state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    var timelineDuration by remember { mutableLongStateOf(0L) }

    // Edit state with proper cropping
    var videoStartTrim by remember { mutableLongStateOf(0L) }
    var videoEndTrim by remember { mutableLongStateOf(0L) }
    var audioStartTrim by remember { mutableLongStateOf(0L) }
    var audioEndTrim by remember { mutableLongStateOf(0L) }
    var audioOffset by remember { mutableLongStateOf(0L) }

    // Volume controls
    var videoVolume by remember { mutableIntStateOf(50) }
    var audioVolume by remember { mutableIntStateOf(100) }
    var showVideoVolumeDialog by remember { mutableStateOf(false) }
    var showAudioVolumeDialog by remember { mutableStateOf(false) }

    // Timeline controls - zoom affects detail level, not time range
    var timelineZoom by remember { mutableFloatStateOf(1f) }
    var timelineScrollOffset by remember { mutableFloatStateOf(0f) }

    // Export state
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportStatus by remember { mutableStateOf("") }

    // Track if we're manually seeking to prevent sync conflicts
    var isManualSeeking by remember { mutableStateOf(false) }

    val merger = remember { AudioVideoMerger(context) }

    // Update durations when players are ready
    LaunchedEffect(videoPlayer, audioPlayer) {
        while (true) {
            if (videoDuration == 0L) {
                val duration = videoPlayer.duration
                if (duration > 0L) {
                    videoDuration = duration
                    videoEndTrim = duration
                }
            }
            if (audioDuration == 0L) {
                val duration = audioPlayer.duration
                if (duration > 0L) {
                    audioDuration = duration
                    audioEndTrim = duration
                }
            }

            // Calculate timeline duration based on the maximum content including offset
            if (videoDuration > 0L && audioDuration > 0L) {
                val videoEffectiveDuration = videoEndTrim - videoStartTrim
                val audioEffectiveEnd = audioOffset + (audioEndTrim - audioStartTrim)
                timelineDuration = maxOf(videoEffectiveDuration, audioEffectiveEnd) + 10000L // Add 10 seconds buffer
            }

            // Update current position only if not manually seeking
            if (isPlaying && !isManualSeeking) {
                currentPosition = videoPlayer.currentPosition
            }

            delay(100)
        }
    }

    // Enhanced sync function with crop awareness
    fun syncPlayersToPosition(position: Long) {
        isManualSeeking = true

        // Calculate actual video position considering trim
        val videoPosition = position + videoStartTrim
        if (videoPosition >= videoStartTrim && videoPosition <= videoEndTrim) {
            videoPlayer.seekTo(videoPosition)
        }

        // Calculate audio position based on offset and trim
        val audioTimelinePosition = position - audioOffset
        val audioPosition = audioTimelinePosition + audioStartTrim

        // Only seek audio if the position is within audio bounds
        if (audioTimelinePosition >= 0 && audioPosition >= audioStartTrim && audioPosition <= audioEndTrim) {
            audioPlayer.seekTo(audioPosition)
        } else {
            audioPlayer.pause()
        }

        currentPosition = position

        // Reset manual seeking flag after a short delay
        scope.launch {
            delay(200)
            isManualSeeking = false
        }
    }

    // Enhanced playback sync with crop awareness
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val videoPosition = currentPosition + videoStartTrim
            val audioTimelinePosition = currentPosition - audioOffset
            val audioPosition = audioTimelinePosition + audioStartTrim

            // Start video player if within trimmed range
            if (videoPosition >= videoStartTrim && videoPosition <= videoEndTrim) {
                videoPlayer.play()
            } else {
                videoPlayer.pause()
            }

            // Handle audio playback based on timeline position and trim
            if (audioTimelinePosition >= 0 && audioPosition >= audioStartTrim && audioPosition <= audioEndTrim) {
                audioPlayer.seekTo(audioPosition)
                audioPlayer.play()
            } else {
                audioPlayer.pause()
            }
        } else {
            videoPlayer.pause()
            audioPlayer.pause()
        }
    }

    // Apply volume changes to players
    LaunchedEffect(videoVolume) {
        videoPlayer.volume = (videoVolume / 100f).coerceIn(0f, 1f)
    }

    LaunchedEffect(audioVolume) {
        audioPlayer.volume = (audioVolume / 100f).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Top control bar
        TopControlBar(
            currentTime = formatTime(currentPosition),
            onBackClick = onBackToCamera,
            onUndoClick = { /* Undo functionality */ },
            onRedoClick = { /* Redo functionality */ },
            timelineZoom = timelineZoom,
            onZoomChange = { timelineZoom = it },
            isExporting = isExporting,
            onExport = {
                isExporting = true
                exportProgress = 0
                exportStatus = "Starting export..."

                merger.timelineEditMerge(
                    videoFile = videoFile,
                    audioFile = audioFile,
                    videoStartTrim = videoStartTrim,
                    videoEndTrim = videoEndTrim,
                    audioStartTrim = audioStartTrim,
                    audioEndTrim = audioEndTrim,
                    audioOffset = audioOffset,
                    audioVolume = audioVolume / 100f,
                    videoVolume = videoVolume / 100f,
                    replaceAudio = false,
                    onProgress = { progress, status ->
                        exportProgress = progress
                        exportStatus = status
                    },
                    onComplete = { result ->
                        isExporting = false
                        if (result != null) {
                            exportStatus = "Saving to gallery..."
                            scope.launch {
                                MediaUtils.saveVideoToGallery(
                                    context = context,
                                    videoFile = result,
                                    onSuccess = { uri ->
                                        exportStatus = "Saved to gallery!"
                                        onExportComplete(result)
                                    },
                                    onError = { error ->
                                        exportStatus = "Export completed, but failed to save to gallery"
                                        onExportComplete(result)
                                    }
                                )
                            }
                        } else {
                            exportStatus = "Export failed"
                        }
                    }
                )
            }
        )

        // Video preview
        VideoPreviewSection(
            videoFile = videoFile,
            videoPlayer = videoPlayer,
            videoVolume = videoVolume,
            showVolumeDialog = showVideoVolumeDialog,
            onVolumeClick = { showVideoVolumeDialog = true },
            modifier = Modifier.weight(1f)
        )

        // Control buttons
        PlaybackControls(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            totalDuration = timelineDuration,
            onPlayPauseClick = {
                isPlaying = !isPlaying
            },
            onSeekBackward = {
                val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                syncPlayersToPosition(newPosition)
            },
            onSeekForward = {
                val newPosition = (currentPosition + 10000).coerceAtMost(timelineDuration)
                syncPlayersToPosition(newPosition)
            }
        )

        // Professional Timeline with improved layout
        ProfessionalTimeline(
            timelineDuration = timelineDuration,
            videoDuration = videoDuration,
            audioDuration = audioDuration,
            currentPosition = currentPosition,
            videoStartTrim = videoStartTrim,
            videoEndTrim = videoEndTrim,
            audioStartTrim = audioStartTrim,
            audioEndTrim = audioEndTrim,
            audioOffset = audioOffset,
            zoom = timelineZoom,
            scrollOffset = timelineScrollOffset,
            videoVolume = videoVolume,
            audioVolume = audioVolume,
            audioFileName = audioFile.nameWithoutExtension,
            onPositionChange = { newPosition ->
                syncPlayersToPosition(newPosition)
            },
            onVideoTrimChange = { start, end ->
                videoStartTrim = start
                videoEndTrim = end
                // Recalculate timeline duration
                val videoEffectiveDuration = end - start
                val audioEffectiveEnd = audioOffset + (audioEndTrim - audioStartTrim)
                timelineDuration = maxOf(videoEffectiveDuration, audioEffectiveEnd) + 10000L

                // If current position is outside trimmed range, seek to start of trim
                if (currentPosition < 0 || currentPosition > (end - start)) {
                    syncPlayersToPosition(0L)
                }
            },
            onAudioTrimChange = { start, end ->
                audioStartTrim = start
                audioEndTrim = end
                // Recalculate timeline duration
                val videoEffectiveDuration = videoEndTrim - videoStartTrim
                val audioEffectiveEnd = audioOffset + (end - start)
                timelineDuration = maxOf(videoEffectiveDuration, audioEffectiveEnd) + 10000L

                // Re-sync players
                syncPlayersToPosition(currentPosition)
            },
            onAudioOffsetChange = { newOffset ->
                audioOffset = newOffset

                // Recalculate timeline duration
                val videoEffectiveDuration = videoEndTrim - videoStartTrim
                val audioEffectiveEnd = newOffset + (audioEndTrim - audioStartTrim)
                timelineDuration = maxOf(videoEffectiveDuration, audioEffectiveEnd) + 10000L

                // Re-sync players with new offset
                syncPlayersToPosition(currentPosition)
            },
            onScrollOffsetChange = { newOffset ->
                timelineScrollOffset = newOffset
            },
            onVideoVolumeClick = { showVideoVolumeDialog = true },
            onAudioVolumeClick = { showAudioVolumeDialog = true }
        )
    }

    // Volume dialogs
    if (showVideoVolumeDialog) {
        VolumeDialog(
            title = "Video Volume",
            volume = videoVolume,
            onVolumeChange = { videoVolume = it },
            onDismiss = { showVideoVolumeDialog = false }
        )
    }

    if (showAudioVolumeDialog) {
        VolumeDialog(
            title = "Audio Volume",
            volume = audioVolume,
            onVolumeChange = { audioVolume = it },
            onDismiss = { showAudioVolumeDialog = false }
        )
    }
}

@Composable
fun TopControlBar(
    currentTime: String,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    timelineZoom: Float,
    onZoomChange: (Float) -> Unit,
    isExporting: Boolean,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Current time display
            Box(
                modifier = Modifier
                    .background(Color(0xFF3D3D3D), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    currentTime,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.width(16.dp))

            // Back button
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Undo/Redo
            IconButton(onClick = onUndoClick) {
                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.Gray)
            }
            IconButton(onClick = onRedoClick) {
                Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.Gray)
            }
        }

        // Center section - Menu
        IconButton(onClick = { }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
        }

        // Right section
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Zoom controls - now for detail level
            IconButton(onClick = { onZoomChange((timelineZoom * 0.7f).coerceAtLeast(0.3f)) }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Less Detail", tint = Color.White)
            }

            Text(
                "${(timelineZoom * 100).roundToInt()}%",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.width(40.dp)
            )

            IconButton(onClick = { onZoomChange((timelineZoom * 1.4f).coerceAtMost(5f)) }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "More Detail", tint = Color.White)
            }

            Spacer(Modifier.width(8.dp))

            // Export button
            Button(
                onClick = onExport,
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                modifier = Modifier.height(36.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.FileUpload, contentDescription = "Export", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text("Export", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun VideoPreviewSection(
    videoFile: File,
    videoPlayer: ExoPlayer,
    videoVolume: Int,
    showVolumeDialog: Boolean,
    onVolumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onVolumeClick() },
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(8.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = videoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Video info overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(bottomEnd = 8.dp))
                .padding(8.dp)
        ) {
            Text(
                videoFile.name,
                color = Color.White,
                fontSize = 12.sp
            )
        }

        // Volume indicator
        if (showVolumeDialog) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text("Video Volume: $videoVolume%", color = Color.White)
            }
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time display
        Text(
            formatTime(currentPosition),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBackward) {
                Icon(Icons.Default.Replay10, contentDescription = "Back 10s", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF007AFF), CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onSeekForward) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // Duration display
        Text(
            formatTime(totalDuration),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
fun TrackControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fileName: String,
    volume: Int,
    color: Color,
    onVolumeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF2D2D2D), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                fileName.take(8),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onVolumeClick() }
        ) {
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = Color.Gray,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "$volume%",
                color = Color.Gray,
                fontSize = 9.sp
            )
        }
    }
}


@Composable
fun VideoThumbnails(
    modifier: Modifier = Modifier,
    zoom: Float = 1f
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Show more thumbnails when zoomed in
        val thumbnailCount = (6 * zoom).toInt().coerceAtLeast(3).coerceAtMost(20)

        repeat(thumbnailCount) {
            Box(
                modifier = Modifier
                    .size((40 / zoom).dp.coerceAtLeast(20.dp))
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            ) {
                Icon(
                    Icons.Default.VideoFile,
                    contentDescription = "Thumbnail",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((16 / zoom).dp.coerceAtLeast(8.dp))
                )
            }
        }
    }
}

@Composable
fun TimelineRuler(
    duration: Long,
    currentPosition: Long,
    zoom: Float,
    scrollOffset: Float,
    onPositionChange: (Long) -> Unit,
    onScrollOffsetChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .background(Color(0xFF2D2D2D), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }

        // Calculate visible time range based on zoom and scroll
        val visibleDuration = duration / zoom
        val visibleStartTime = (scrollOffset * duration).toLong()
        val visibleEndTime = (visibleStartTime + visibleDuration.toLong()).coerceAtMost(duration)

        // Time markers with adaptive intervals based on zoom
        if (duration > 0) {
            val timeInterval = when {
                zoom >= 4f -> 1000L    // 1 second intervals when very zoomed in
                zoom >= 2f -> 5000L    // 5 second intervals
                zoom >= 1f -> 10000L   // 10 second intervals
                else -> 30000L         // 30 second intervals when zoomed out
            }

            for (time in ((visibleStartTime / timeInterval) * timeInterval)..visibleEndTime step timeInterval) {
                if (time >= 0) {
                    val ratio = (time - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
                    val xOffset = maxWidth * ratio

                    if (xOffset >= 0.dp && xOffset <= maxWidth) {
                        Column(
                            modifier = Modifier.offset(x = xOffset)
                        ) {
                            Text(
                                formatTime(time),
                                color = Color.White,
                                fontSize = (8 + zoom * 2).sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(8.dp)
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }
        }

        // Playhead - positioned relative to visible timeline
        if (duration > 0 && currentPosition >= visibleStartTime && currentPosition <= visibleEndTime) {
            val positionRatio = (currentPosition - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
            val positionOffset = maxWidth * positionRatio

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .offset(x = positionOffset - 1.5.dp)
                    .background(Color.Red)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newRatio = (offset.x / timelineWidthPx).coerceIn(0f, 1f)
                                val newPosition = (visibleStartTime + (visibleEndTime - visibleStartTime) * newRatio).toLong()
                                onPositionChange(newPosition)
                            }
                        ) { change, _ ->
                            val newRatio = (change.position.x / timelineWidthPx).coerceIn(0f, 1f)
                            val newPosition = (visibleStartTime + (visibleEndTime - visibleStartTime) * newRatio).toLong()
                            onPositionChange(newPosition)
                        }
                    }
            ) {
                // Playhead indicator at top
                Box(
                    modifier = Modifier
                        .size(10.dp, 6.dp)
                        .offset(x = (-3.5).dp, y = (-3).dp)
                        .background(Color.Red, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                )
            }
        }

        // Timeline click handler for direct positioning
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (duration > 0) {
                                val ratio = (offset.x / timelineWidthPx).coerceIn(0f, 1f)
                                val newPosition = (visibleStartTime + (visibleEndTime - visibleStartTime) * ratio).toLong()
                                onPositionChange(newPosition)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            // Handle horizontal scrolling when dragging beyond edges
                            if (change.position.x < 0 || change.position.x > timelineWidthPx) {
                                val scrollDelta = dragAmount.x / timelineWidthPx * 0.1f
                                val newScrollOffset = (scrollOffset - scrollDelta).coerceIn(0f, 1f - (1f / zoom))
                                onScrollOffsetChange(newScrollOffset)
                            }
                        }
                    )
                }
        )
    }
}

@Composable
fun VolumeDialog(
    title: String,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Adjust volume level",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

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
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF007AFF),
                            activeTrackColor = Color(0xFF007AFF),
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

                Text(
                    "$volume%",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF))
            ) {
                Text("Done", fontWeight = FontWeight.Medium)
            }
        },
        containerColor = Color(0xFF2D2D2D),
        shape = RoundedCornerShape(12.dp)
    )
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun ProfessionalTimeline(
    timelineDuration: Long,
    videoDuration: Long,
    audioDuration: Long,
    currentPosition: Long,
    videoStartTrim: Long,
    videoEndTrim: Long,
    audioStartTrim: Long,
    audioEndTrim: Long,
    audioOffset: Long,
    zoom: Float,
    scrollOffset: Float,
    videoVolume: Int,
    audioVolume: Int,
    audioFileName: String,
    onPositionChange: (Long) -> Unit,
    onVideoTrimChange: (Long, Long) -> Unit,
    onAudioTrimChange: (Long, Long) -> Unit,
    onAudioOffsetChange: (Long) -> Unit,
    onScrollOffsetChange: (Float) -> Unit,
    onVideoVolumeClick: () -> Unit,
    onAudioVolumeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp)
    ) {
        // Main timeline container
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Timeline header with time ruler
                TimelineHeader(
                    duration = timelineDuration,
                    currentPosition = currentPosition,
                    zoom = zoom,
                    scrollOffset = scrollOffset,
                    onPositionChange = onPositionChange,
                    onScrollOffsetChange = onScrollOffsetChange
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Audio track
                TrackSection(
                    title = "Audio",
                    subtitle = audioFileName.take(20),
                    color = Color(0xFF00C853),
                    volume = audioVolume,
                    onVolumeClick = onAudioVolumeClick
                ) {
                    AudioTrackEnhanced(
                        duration = audioDuration,
                        timelineDuration = timelineDuration,
                        startTrim = audioStartTrim,
                        endTrim = audioEndTrim,
                        offset = audioOffset,
                        zoom = zoom,
                        scrollOffset = scrollOffset,
                        onTrimChange = onAudioTrimChange,
                        onOffsetChange = onAudioOffsetChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Video track
                TrackSection(
                    title = "Video",
                    subtitle = "Main video track",
                    color = Color(0xFF2196F3),
                    volume = videoVolume,
                    onVolumeClick = onVideoVolumeClick
                ) {
                    VideoTrackEnhanced(
                        duration = videoDuration,
                        timelineDuration = timelineDuration,
                        startTrim = videoStartTrim,
                        endTrim = videoEndTrim,
                        zoom = zoom,
                        scrollOffset = scrollOffset,
                        onTrimChange = onVideoTrimChange
                    )
                }
            }
        }

        // Timeline controls
        Spacer(modifier = Modifier.height(12.dp))
        TimelineControls(
            zoom = zoom,
            onZoomChange = { /* Handle in parent */ }
        )
    }
}

@Composable
fun TimelineHeader(
    duration: Long,
    currentPosition: Long,
    zoom: Float,
    scrollOffset: Float,
    onPositionChange: (Long) -> Unit,
    onScrollOffsetChange: (Float) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(
                Color(0xFF2D2D2D),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }
        val visibleDuration = duration / zoom
        val visibleStartTime = (scrollOffset * duration).toLong()
        val visibleEndTime = (visibleStartTime + visibleDuration.toLong()).coerceAtMost(duration)

        // Time markers
        if (duration > 0) {
            val timeInterval = when {
                zoom >= 4f -> 1000L
                zoom >= 2f -> 5000L
                zoom >= 1f -> 10000L
                else -> 30000L
            }

            for (time in ((visibleStartTime / timeInterval) * timeInterval)..visibleEndTime step timeInterval) {
                if (time >= 0) {
                    val ratio = (time - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
                    val xOffset = maxWidth * ratio

                    if (xOffset >= 0.dp && xOffset <= maxWidth) {
                        Column(
                            modifier = Modifier.offset(x = xOffset),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                formatTime(time),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        }

        // Playhead
        if (duration > 0 && currentPosition >= visibleStartTime && currentPosition <= visibleEndTime) {
            val positionRatio = (currentPosition - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
            val positionOffset = maxWidth * positionRatio

            Column(
                modifier = Modifier.offset(x = positionOffset - 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current time display
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xFFFF4444),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Playhead line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(Color(0xFFFF4444))
                )
            }
        }

        // Interaction overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (duration > 0) {
                                val ratio = (offset.x / timelineWidthPx).coerceIn(0f, 1f)
                                val newPosition = (visibleStartTime + (visibleEndTime - visibleStartTime) * ratio).toLong()
                                onPositionChange(newPosition)
                            }
                        }
                    ) { change, dragAmount ->
                        if (duration > 0) {
                            val ratio = (change.position.x / timelineWidthPx).coerceIn(0f, 1f)
                            val newPosition = (visibleStartTime + (visibleEndTime - visibleStartTime) * ratio).toLong()
                            onPositionChange(newPosition)
                        }
                    }
                }
        )
    }
}

@Composable
fun TrackSection(
    title: String,
    subtitle: String,
    color: Color,
    volume: Int,
    onVolumeClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        // Track header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )

                Column {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Volume control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onVolumeClick() }
                    .background(
                        Color.Gray.copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "$volume%",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track content
        content()
    }
}

@Composable
fun AudioTrackEnhanced(
    duration: Long,
    timelineDuration: Long,
    startTrim: Long,
    endTrim: Long,
    offset: Long,
    zoom: Float,
    scrollOffset: Float,
    onTrimChange: (Long, Long) -> Unit,
    onOffsetChange: (Long) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Color(0xFF2A2A2A),
                RoundedCornerShape(8.dp)
            )
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }
        val visibleStartTime = (scrollOffset * timelineDuration).toLong()
        val visibleEndTime = visibleStartTime + (timelineDuration / zoom).toLong()

        // Grid background
        TimelineGridBackground(
            duration = timelineDuration,
            zoom = zoom,
            scrollOffset = scrollOffset,
            modifier = Modifier.fillMaxSize()
        )

        if (duration > 0 && timelineDuration > 0) {
            val effectiveDuration = endTrim - startTrim
            val audioStartInTimeline = offset
            val audioEndInTimeline = offset + effectiveDuration

            if (audioEndInTimeline > visibleStartTime && audioStartInTimeline < visibleEndTime) {
                val startRatio = (audioStartInTimeline - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
                val durationRatio = effectiveDuration.toFloat() / (visibleEndTime - visibleStartTime)
                val startOffsetDp = (maxWidth * startRatio).coerceAtLeast(0.dp)
                val trackWidth = (maxWidth * durationRatio).coerceAtMost(maxWidth - startOffsetDp)

                if (trackWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(trackWidth)
                            .fillMaxHeight()
                            .offset(x = startOffsetDp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00C853).copy(alpha = 0.8f),
                                        Color(0xFF00E676).copy(alpha = 0.6f)
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    val deltaTime = (dragAmount.x / timelineWidthPx * (visibleEndTime - visibleStartTime)).toLong()
                                    val newOffset = (offset + deltaTime)
                                        .coerceAtLeast(0L)
                                        .coerceAtMost(timelineDuration - effectiveDuration)
                                    onOffsetChange(newOffset)
                                }
                            }
                    ) {
                        // Waveform visualization
                        AudioWaveformModern(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            zoom = zoom
                        )

                        // Trim handles
                        TrimHandle(
                            isStart = true,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onDrag = { dragAmount ->
                                val deltaTime = (dragAmount.x / timelineWidthPx * duration).toLong()
                                val newStart = (startTrim + deltaTime)
                                    .coerceAtLeast(0L)
                                    .coerceAtMost(endTrim - 1000L)
                                onTrimChange(newStart, endTrim)
                            }
                        )

                        TrimHandle(
                            isStart = false,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onDrag = { dragAmount ->
                                val deltaTime = (dragAmount.x / timelineWidthPx * duration).toLong()
                                val newEnd = (endTrim + deltaTime)
                                    .coerceAtLeast(startTrim + 1000L)
                                    .coerceAtMost(duration)
                                onTrimChange(startTrim, newEnd)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoTrackEnhanced(
    duration: Long,
    timelineDuration: Long,
    startTrim: Long,
    endTrim: Long,
    zoom: Float,
    scrollOffset: Float,
    onTrimChange: (Long, Long) -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Color(0xFF2A2A2A),
                RoundedCornerShape(8.dp)
            )
    ) {
        val timelineWidthPx = with(density) { maxWidth.toPx() }
        val visibleStartTime = (scrollOffset * timelineDuration).toLong()
        val visibleEndTime = visibleStartTime + (timelineDuration / zoom).toLong()

        // Grid background
        TimelineGridBackground(
            duration = timelineDuration,
            zoom = zoom,
            scrollOffset = scrollOffset,
            modifier = Modifier.fillMaxSize()
        )

        if (duration > 0 && timelineDuration > 0) {
            val effectiveDuration = endTrim - startTrim
            val videoStartInTimeline = 0L
            val videoEndInTimeline = effectiveDuration

            if (videoEndInTimeline > visibleStartTime && videoStartInTimeline < visibleEndTime) {
                val startRatio = (videoStartInTimeline - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
                val durationRatio = effectiveDuration.toFloat() / (visibleEndTime - visibleStartTime)
                val startOffsetDp = (maxWidth * startRatio).coerceAtLeast(0.dp)
                val trackWidth = (maxWidth * durationRatio).coerceAtMost(maxWidth - startOffsetDp)

                if (trackWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(trackWidth)
                            .fillMaxHeight()
                            .offset(x = startOffsetDp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2196F3).copy(alpha = 0.8f),
                                        Color(0xFF64B5F6).copy(alpha = 0.6f)
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        // Video thumbnails
                        VideoThumbnailsModern(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            zoom = zoom
                        )

                        // Trim handles
                        TrimHandle(
                            isStart = true,
                            modifier = Modifier.align(Alignment.CenterStart),
                            onDrag = { dragAmount ->
                                val deltaTime = (dragAmount.x / timelineWidthPx * duration).toLong()
                                val newStart = (startTrim + deltaTime)
                                    .coerceAtLeast(0L)
                                    .coerceAtMost(endTrim - 1000L)
                                onTrimChange(newStart, endTrim)
                            }
                        )

                        TrimHandle(
                            isStart = false,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onDrag = { dragAmount ->
                                val deltaTime = (dragAmount.x / timelineWidthPx * duration).toLong()
                                val newEnd = (endTrim + deltaTime)
                                    .coerceAtLeast(startTrim + 1000L)
                                    .coerceAtMost(duration)
                                onTrimChange(startTrim, newEnd)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrimHandle(
    isStart: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .width(16.dp)
            .height(48.dp)
            .background(
                Color.White,
                RoundedCornerShape(if (isStart) 8.dp else 8.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    onDrag(dragAmount)
                }
            }
    ) {
        // Handle indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .align(Alignment.Center)
                .background(
                    Color.Gray,
                    RoundedCornerShape(1.dp)
                )
        )

        // Direction arrow
        Icon(
            if (isStart) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = if (isStart) "Trim start" else "Trim end",
            tint = Color.Gray,
            modifier = Modifier
                .align(Alignment.Center)
                .size(12.dp)
        )
    }
}

@Composable
fun TimelineGridBackground(
    duration: Long,
    zoom: Float,
    scrollOffset: Float,
    modifier: Modifier = Modifier
) {
    val visibleStartTime = (scrollOffset * duration).toLong()
    val visibleEndTime = visibleStartTime + (duration / zoom).toLong()

    Canvas(modifier = modifier) {
        if (duration > 0) {
            val gridInterval = when {
                zoom >= 4f -> 5000L
                zoom >= 2f -> 10000L
                else -> 30000L
            }

            for (time in ((visibleStartTime / gridInterval) * gridInterval)..visibleEndTime step gridInterval) {
                if (time >= 0) {
                    val ratio = (time - visibleStartTime).toFloat() / (visibleEndTime - visibleStartTime)
                    val x = size.width * ratio

                    if (x >= 0 && x <= size.width) {
                        drawLine(
                            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f),
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveformModern(
    modifier: Modifier = Modifier,
    zoom: Float = 1f
) {
    val waveformData = remember(zoom) {
        val sampleCount = (60 * zoom).toInt().coerceAtLeast(30).coerceAtMost(200)
        (0..sampleCount).map { Random.nextFloat() * 0.8f + 0.1f }
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / waveformData.size
        waveformData.forEachIndexed { index, amplitude ->
            val barHeight = size.height * amplitude
            val x = index * barWidth
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            )
        }
    }
}

@Composable
fun VideoThumbnailsModern(
    modifier: Modifier = Modifier,
    zoom: Float = 1f
) {
    val thumbnailCount = (6 * zoom).toInt().coerceAtLeast(3).coerceAtMost(12)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(thumbnailCount) { index ->
            Box(
                modifier = Modifier
                    .size(40.dp, 32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Frame $index",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineControls(
    zoom: Float,
    onZoomChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF1C1C1C),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Timeline Controls",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Zoom",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Text(
                "${(zoom * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}