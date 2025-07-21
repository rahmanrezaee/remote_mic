package com.example.remote_mic.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.remote_mic.AppState
import com.example.remote_mic.CameraManager
import java.io.File
import java.util.*

// Data classes for media management
data class RecordedMedia(
    val id: String,
    val name: String,
    val path: String,
    val type: MediaType,
    val duration: Long,
    val timestamp: Long,
    val size: Long,
    val thumbnail: String? = null
)

enum class MediaType {
    VIDEO, AUDIO, MERGED
}



data class ConnectedDevice(
    val name: String,
    val id: String
)

data class CameraState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDuration: Long = 0L,
    val isFlashOn: Boolean = false,
    val errorMessage: String? = null
)

// Add interface for CameraManager
interface CameraManager {
    val state: kotlinx.coroutines.flow.StateFlow<CameraState>
    fun initializeCamera(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner)
    fun startRecording()
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording()
    fun toggleFlash()
    fun switchCamera()
}

@Composable
fun CameraScreen(
    appState: com.example.remote_mic.AppState,
    cameraManager: CameraManager,
    onDisconnect: () -> Unit,
    onClearAudioFile: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraState by cameraManager.state.collectAsStateWithLifecycle()

    var showRecordingsList by remember { mutableStateOf(false) }
    var showProcessingLoader by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var recordedVideos by remember { mutableStateOf<List<RecordedMedia>>(emptyList()) }
    var receivedAudios by remember { mutableStateOf<List<RecordedMedia>>(emptyList()) }

    // Initialize camera when screen loads
    LaunchedEffect(Unit) {
        previewView?.let { preview ->
            cameraManager.initializeCamera(preview, lifecycleOwner)
        }
        // Load existing recordings
        loadRecordings(context) { videos, audios ->
            recordedVideos = videos
            receivedAudios = audios
        }
    }

    // Handle recording commands and states
    LaunchedEffect(appState.recordingState) {
        when (appState.recordingState) {
            "recording" -> if (!cameraState.isRecording) {
                cameraManager.startRecording()
            }
            "paused" -> if (cameraState.isRecording && !cameraState.isPaused) {
                cameraManager.pauseRecording()
            }
            "idle" -> if (cameraState.isRecording) {
                cameraManager.stopRecording()
                showProcessingLoader = true
            }
        }
    }

    // Handle audio file received
    LaunchedEffect(appState.receivedAudioFile) {
        appState.receivedAudioFile?.let { audioFile ->
            val newAudio = RecordedMedia(
                id = UUID.randomUUID().toString(),
                name = audioFile.name,
                path = audioFile.absolutePath,
                type = MediaType.AUDIO,
                duration = getAudioDuration(audioFile),
                timestamp = System.currentTimeMillis(),
                size = audioFile.length()
            )
            receivedAudios = receivedAudios + newAudio
            showProcessingLoader = false
            showRecordingsList = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showRecordingsList) {
            RecordingsListScreen(
                videos = recordedVideos,
                audios = receivedAudios,
                onBack = { showRecordingsList = false },
                onMergeMedia = { video, audio ->
                    mergeVideoWithAudio(context, video, audio) { success ->
                        if (success) {
                            loadRecordings(context) { videos, audios ->
                                recordedVideos = videos
                                receivedAudios = audios
                            }
                        }
                    }
                },
                onDeleteMedia = { media ->
                    deleteMedia(context, media)
                    loadRecordings(context) { videos, audios ->
                        recordedVideos = videos
                        receivedAudios = audios
                    }
                },
                onShareMedia = { media ->
                    shareMedia(context, media)
                }
            )
        } else {
            CameraPreviewInterface(
                cameraState = cameraState,
                appState = appState,
                previewView = previewView,
                onPreviewViewCreated = { previewView = it },
                onStartRecording = { cameraManager.startRecording() },
                onPauseRecording = { cameraManager.pauseRecording() },
                onResumeRecording = { cameraManager.resumeRecording() },
                onStopRecording = { cameraManager.stopRecording() },
                onToggleFlash = { cameraManager.toggleFlash() },
                onSwitchCamera = { cameraManager.switchCamera() },
                onDisconnect = onDisconnect,
                onShowRecordings = { showRecordingsList = true }
            )
        }

        // Processing Loader Overlay
        if (showProcessingLoader) {
            ProcessingLoaderOverlay(
                message = "Waiting for audio from microphone device...",
                onCancel = {
                    showProcessingLoader = false
                    showRecordingsList = true
                }
            )
        }

        // Audio Received Notification
        appState.receivedAudioFile?.let { audioFile ->
            AudioReceivedNotification(
                audioFile = audioFile,
                onDismiss = {
                    onClearAudioFile()
                    showProcessingLoader = false
                }
            )
        }

        // Error Messages
        cameraState.errorMessage?.let { error ->
            ErrorMessageCard(
                message = error,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun CameraPreviewInterface(
    cameraState: com.example.remote_mic.CameraState,
    appState: com.example.remote_mic.AppState,
    previewView: PreviewView?,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onDisconnect: () -> Unit,
    onShowRecordings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview with Gradient Overlay
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        onPreviewViewCreated(this)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlays for better UI visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
        }

        // Top Status Bar
        TopStatusBar(
            cameraState = cameraState,
            appState = appState,
            onToggleFlash = onToggleFlash,
            onDisconnect = onDisconnect,
            onShowRecordings = onShowRecordings,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Recording Controls
        BottomRecordingControls(
            cameraState = cameraState,
            onStartRecording = onStartRecording,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onStopRecording = onStopRecording,
            onSwitchCamera = onSwitchCamera,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Side Controls
        SideControls(
            cameraState = cameraState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun TopStatusBar(
    cameraState: com.example.remote_mic.CameraState,
    appState: AppState,
    onToggleFlash: () -> Unit,
    onDisconnect: () -> Unit,
    onShowRecordings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Recording Status
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated recording indicator
                val infiniteTransition = rememberInfiniteTransition(label = "recording")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (cameraState.isRecording && !cameraState.isPaused) 0.3f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            when {
                                cameraState.isRecording && !cameraState.isPaused ->
                                    Color.Red.copy(alpha = alpha)
                                cameraState.isPaused -> Color.Yellow
                                else -> Color.Gray
                            },
                            CircleShape
                        )
                )

                Column {
                    Text(
                        text = when {
                            cameraState.isRecording && !cameraState.isPaused -> "REC"
                            cameraState.isPaused -> "PAUSED"
                            else -> "READY"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (cameraState.recordingDuration > 0) {
                        Text(
                            text = formatDuration(cameraState.recordingDuration),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Connection Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = appState.connectedDevice?.name?.take(8) ?: "Connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Flash Button
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (cameraState.isFlashOn)
                            Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (cameraState.isFlashOn) Color.Yellow else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Recordings Button
                IconButton(
                    onClick = onShowRecordings,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Recordings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Disconnect Button
                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.Red.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomRecordingControls(
    cameraState: com.example.remote_mic.CameraState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(30.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Switch Button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
                    .clickable { onSwitchCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Main Record Button
            val buttonScale by animateFloatAsState(
                targetValue = if (cameraState.isRecording) 0.9f else 1f,
                animationSpec = tween(150),
                label = "buttonScale"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(buttonScale)
                    .background(
                        when {
                            cameraState.isRecording && !cameraState.isPaused -> Color.Red
                            cameraState.isPaused -> Color.Yellow
                            else -> Color.White
                        },
                        CircleShape
                    )
                    .border(
                        4.dp,
                        Color.White.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .clickable {
                        when {
                            !cameraState.isRecording -> onStartRecording()
                            cameraState.isPaused -> onResumeRecording()
                            else -> onPauseRecording()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !cameraState.isRecording -> Icons.Default.PlayArrow
                        cameraState.isPaused -> Icons.Default.PlayArrow
                        else -> Icons.Default.Pause
                    },
                    contentDescription = "Record",
                    tint = when {
                        cameraState.isRecording -> Color.White
                        else -> Color.Black
                    },
                    modifier = Modifier.size(36.dp)
                )
            }

            // Stop Button
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        if (cameraState.isRecording)
                            Color.Red.copy(alpha = 0.8f)
                        else
                            Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .clickable(enabled = cameraState.isRecording) {
                        onStopRecording()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun SideControls(
    cameraState: com.example.remote_mic.CameraState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Recording Quality Indicator
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "1080p",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Audio Sync Indicator
        if (cameraState.isRecording) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Green.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingLoaderOverlay(
    message: String,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Animated loader
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .rotate(rotation)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "This may take a few moments...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun AudioReceivedNotification(
    audioFile: File,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Audio Received!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = audioFile.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Later")
                }
                Button(onClick = onDismiss) {
                    Text("View Recordings")
                }
            }
        }
    }
}

@Composable
fun ErrorMessageCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun RecordingsListScreen(
    videos: List<RecordedMedia>,
    audios: List<RecordedMedia>,
    onBack: () -> Unit,
    onMergeMedia: (RecordedMedia, RecordedMedia) -> Unit,
    onDeleteMedia: (RecordedMedia) -> Unit,
    onShareMedia: (RecordedMedia) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<RecordedMedia?>(null) }
    var selectedAudio by remember { mutableStateOf<RecordedMedia?>(null) }

    val tabs = listOf(
        "Videos" to Icons.Default.Videocam,
        "Audio" to Icons.Default.Mic,
        "Merged" to Icons.Default.Movie
    )

    val mergedVideos = videos.filter { it.type == MediaType.MERGED }
    val videoOnly = videos.filter { it.type == MediaType.VIDEO }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Recordings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Merge Button
                if (videoOnly.isNotEmpty() && audios.isNotEmpty()) {
                    IconButton(onClick = { showMergeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.MergeType,
                            contentDescription = "Merge Audio & Video",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(title)
                        }
                    }
                )
            }
        }

        // Content based on selected tab
        when (selectedTab) {
            0 -> VideosGrid(
                videos = videoOnly,
                onDelete = onDeleteMedia,
                onShare = onShareMedia,
                onSelectForMerge = { video ->
                    selectedVideo = video
                    showMergeDialog = true
                }
            )
            1 -> AudiosList(
                audios = audios,
                onDelete = onDeleteMedia,
                onShare = onShareMedia,
                onSelectForMerge = { audio ->
                    selectedAudio = audio
                    showMergeDialog = true
                }
            )
            2 -> MergedVideosGrid(
                videos = mergedVideos,
                onDelete = onDeleteMedia,
                onShare = onShareMedia
            )
        }
    }
    // Merge Dialog
    if (showMergeDialog) {
        MergeMediaDialog(
            videos = videoOnly,
            audios = audios,
            selectedVideo = selectedVideo,
            selectedAudio = selectedAudio,
            onDismiss = {
                showMergeDialog = false
                selectedVideo = null
                selectedAudio = null
            },
            onMerge = { video, audio ->
                showMergeDialog = false
                onMergeMedia(video, audio)
                selectedVideo = null
                selectedAudio = null
            }
        )
    }
}

@Composable
fun VideosGrid(
    videos: List<RecordedMedia>,
    onDelete: (RecordedMedia) -> Unit,
    onShare: (RecordedMedia) -> Unit,
    onSelectForMerge: (RecordedMedia) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Videocam,
            title = "No Videos",
            subtitle = "Start recording to see your videos here"
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                VideoCard(
                    video = video,
                    onDelete = { onDelete(video) },
                    onShare = { onShare(video) },
                    onSelectForMerge = { onSelectForMerge(video) }
                )
            }
        }
    }
}

@Composable
fun AudiosList(
    audios: List<RecordedMedia>,
    onDelete: (RecordedMedia) -> Unit,
    onShare: (RecordedMedia) -> Unit,
    onSelectForMerge: (RecordedMedia) -> Unit
) {
    if (audios.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Mic,
            title = "No Audio Files",
            subtitle = "Audio files from microphone device will appear here"
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(audios) { audio ->
                AudioCard(
                    audio = audio,
                    onDelete = { onDelete(audio) },
                    onShare = { onShare(audio) },
                    onSelectForMerge = { onSelectForMerge(audio) }
                )
            }
        }
    }
}

@Composable
fun MergedVideosGrid(
    videos: List<RecordedMedia>,
    onDelete: (RecordedMedia) -> Unit,
    onShare: (RecordedMedia) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Movie,
            title = "No Merged Videos",
            subtitle = "Merge video and audio files to create complete recordings"
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                MergedVideoCard(
                    video = video,
                    onDelete = { onDelete(video) },
                    onShare = { onShare(video) }
                )
            }
        }
    }
}

@Composable
fun VideoCard(
    video: RecordedMedia,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSelectForMerge: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Video Thumbnail Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Duration Badge
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // Menu Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Merge with Audio") },
                        onClick = {
                            showMenu = false
                            onSelectForMerge()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.MergeType, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }

            // Video Info
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = video.name.take(20) + if (video.name.length > 20) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${formatFileSize(video.size)} • ${formatTimestamp(video.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioCard(
    audio: RecordedMedia,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSelectForMerge: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    )
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Audio Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = audio.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${formatDuration(audio.duration)} • ${formatFileSize(audio.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(audio.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menu Button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Merge with Video") },
                        onClick = {
                            showMenu = false
                            onSelectForMerge()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.MergeType, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MergedVideoCard(
    video: RecordedMedia,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Video Thumbnail with Merged Badge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "MERGED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Duration Badge
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // Menu Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }

            // Video Info
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = video.name.take(20) + if (video.name.length > 20) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${formatFileSize(video.size)} • ${formatTimestamp(video.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MergeMediaDialog(
    videos: List<RecordedMedia>,
    audios: List<RecordedMedia>,
    selectedVideo: RecordedMedia?,
    selectedAudio: RecordedMedia?,
    onDismiss: () -> Unit,
    onMerge: (RecordedMedia, RecordedMedia) -> Unit
) {
    var chosenVideo by remember { mutableStateOf(selectedVideo) }
    var chosenAudio by remember { mutableStateOf(selectedAudio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MergeType,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Merge Audio & Video")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select a video and audio file to merge:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Video Selection
                Text(
                    text = "Video:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                videos.take(3).forEach { video ->
                    Card(
                        onClick = { chosenVideo = video },
                        colors = CardDefaults.cardColors(
                            containerColor = if (chosenVideo?.id == video.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = video.name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Audio Selection
                Text(
                    text = "Audio:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                audios.take(3).forEach { audio ->
                    Card(
                        onClick = { chosenAudio = audio },
                        colors = CardDefaults.cardColors(
                            containerColor = if (chosenAudio?.id == audio.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = audio.name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chosenVideo != null && chosenAudio != null) {
                        onMerge(chosenVideo!!, chosenAudio!!)
                    }
                },
                enabled = chosenVideo != null && chosenAudio != null
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions for file operations
fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}

// File operation functions - implement these based on your needs
fun loadRecordings(
    context: Context,
    onLoaded: (List<RecordedMedia>, List<RecordedMedia>) -> Unit
) {
    // Implementation to scan and load recordings from storage
    // For now, return empty lists - implement based on your file structure
    val recordingsDir = java.io.File(context.getExternalFilesDir(null), "RemoteMicRecordings")
    val videos = mutableListOf<RecordedMedia>()
    val audios = mutableListOf<RecordedMedia>()

    if (recordingsDir.exists()) {
        recordingsDir.listFiles()?.forEach { file ->
            when (file.extension.lowercase()) {
                "mp4", "mov", "avi" -> {
                    videos.add(
                        RecordedMedia(
                            id = UUID.randomUUID().toString(),
                            name = file.name,
                            path = file.absolutePath,
                            type = if (file.name.contains("merged", ignoreCase = true))
                                MediaType.MERGED else MediaType.VIDEO,
                            duration = 0L, // Get actual duration using MediaMetadataRetriever
                            timestamp = file.lastModified(),
                            size = file.length()
                        )
                    )
                }
                "m4a", "mp3", "wav", "aac" -> {
                    audios.add(
                        RecordedMedia(
                            id = UUID.randomUUID().toString(),
                            name = file.name,
                            path = file.absolutePath,
                            type = MediaType.AUDIO,
                            duration = 0L, // Get actual duration using MediaMetadataRetriever
                            timestamp = file.lastModified(),
                            size = file.length()
                        )
                    )
                }
            }
        }
    }

    onLoaded(videos.sortedByDescending { it.timestamp }, audios.sortedByDescending { it.timestamp })
}

fun getAudioDuration(file: File): Long {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        duration?.toLongOrNull() ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun mergeVideoWithAudio(
    context: Context,
    video: RecordedMedia,
    audio: RecordedMedia,
    onComplete: (Boolean) -> Unit
) {
    // Implementation for merging video and audio using FFmpeg
    // This is a complex operation that requires FFmpeg library
    // For now, simulate the process
    Thread {
        try {
            // Simulate processing time
            Thread.sleep(3000)

            // Create merged file name
            val timestamp = System.currentTimeMillis()
            val mergedFileName = "merged_video_$timestamp.mp4"
            val recordingsDir = java.io.File(context.getExternalFilesDir(null), "RemoteMicRecordings")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()

            val mergedFile = java.io.File(recordingsDir, mergedFileName)

            // In real implementation, use FFmpeg to combine video and audio
            // For demo, just copy the video file
            java.io.File(video.path).copyTo(mergedFile, overwrite = true)

            onComplete(true)
        } catch (e: Exception) {
            onComplete(false)
        }
    }.start()
}

fun deleteMedia(context: Context, media: RecordedMedia) {
    try {
        val file = java.io.File(media.path)
        if (file.exists()) {
            file.delete()
        }
    } catch (e: Exception) {
        // Handle error - could show toast or log error
    }
}

fun shareMedia(context: Context, media: RecordedMedia) {
    try {
        val file = java.io.File(media.path)
        if (file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                type = when (media.type) {
                    MediaType.VIDEO, MediaType.MERGED -> "video/*"
                    MediaType.AUDIO -> "audio/*"
                }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                android.content.Intent.createChooser(shareIntent, "Share ${media.type.name.lowercase()}")
            )
        }
    } catch (e: Exception) {
        // Handle error - could show toast or log error
    }
}