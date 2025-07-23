package com.example.remote_mic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import com.example.remote_mic.managers.AudioVideoMerger
import java.io.File

@Composable
fun MergeEditScreen(
    videoFile: File,
    audioFile: File,
    onBackToCamera: () -> Unit,
    onMergeComplete: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Video player
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoFile.toURI().toString()))
            prepare()
        }
    }
    DisposableEffect(videoPlayer) { onDispose { videoPlayer.release() } }

    // Audio player
    val audioPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(audioPlayer) { onDispose { audioPlayer.release() } }

    var isAudioPlaying by remember { mutableStateOf(false) }
    var isMerging by remember { mutableStateOf(false) }
    var mergeStatus by remember { mutableStateOf("Ready to merge") }
    var mergeProgress by remember { mutableIntStateOf(0) }
    var mergedFile by remember { mutableStateOf<File?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }

    val merger = remember { AudioVideoMerger(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Back button and header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackToCamera) { Text("Back") }
            Spacer(Modifier.width(18.dp))
            Text(
                "Simple Video/Audio Merger",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        // Video Preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text("File: ${videoFile.name}", color = Color.White)

        // Audio File play button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    audioPlayer.setMediaItem(MediaItem.fromUri(audioFile.toURI().toString()))
                    audioPlayer.prepare()
                    audioPlayer.play()
                    isAudioPlaying = true
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.18f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(audioFile.nameWithoutExtension, color = Color.White)
                if (isAudioPlaying) {
                    Text(" (playing)", color = Color.Green, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Merge button
        Button(
            onClick = { showMergeDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Default.Merge, contentDescription = "Merge", tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Merge Audio to Video", color = Color.White)
        }

        // Merging progress
        if (isMerging) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.20f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Merging...", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = mergeProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(mergeStatus, color = Color.White)
                }
            }
        }

        // Show merge result
        if (mergedFile != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3).copy(alpha = 0.20f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Merged Video Saved!", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Path: ${mergedFile?.absolutePath}", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onMergeComplete(mergedFile!!) }) {
                        Text("Continue", color = Color.White)
                    }
                }
            }
        }
    }

    // Merge confirmation dialog
    if (showMergeDialog) {
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Audio & Video", color = Color.White) },
            text = { Text("Do you want to merge the audio file into the video?", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        showMergeDialog = false
                        isMerging = true
                        mergeProgress = 0
                        mergeStatus = "Starting merge..."
                        // Use your real merger logic!
                        val options = AudioVideoMerger.MergeOptions(
                            audioVolume = 1.0f,
                            replaceAudio = true
                        )
                        merger.mergeAudioVideo(
                            videoFile = videoFile,
                            audioFile = audioFile,
                            options = options,
                            onProgress = { progress, status ->
                                mergeProgress = progress
                                mergeStatus = status
                            },
                            onComplete = { result ->
                                isMerging = false
                                if (result != null) {
                                    mergedFile = result
                                    mergeStatus = "Export completed successfully!"
                                } else {
                                    mergeStatus = "Export failed. Please try again."
                                }
                            }
                        )
                    }
                ) {
                    Text("Merge", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}