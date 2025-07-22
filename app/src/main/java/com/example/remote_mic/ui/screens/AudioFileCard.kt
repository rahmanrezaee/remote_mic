package com.example.remote_mic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remote_mic.utils.formatFileSize
import com.example.remote_mic.utils.openMediaFile
import java.io.File

@Composable
fun AudioFileCard(
    audioFile: File,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🎵 Audio Ready to Send", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPreview() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎤 ", fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        audioFile.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${formatFileSize(audioFile.length())} • Tap to preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sending...")
                } else {
                    Text("📤 Send to Camera Device")
                }
            }
        }
    }
}

@Composable
fun FileStatusCard(
    videoFile: File?,
    audioFile: File?
) {
    val context = LocalContext.current

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📁 Recorded Files", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(8.dp))

            videoFile?.let { file ->
                FileRow(
                    icon = "📹",
                    label = "Video",
                    file = file,
                    onClick = { openMediaFile(context, file) }
                )
            }

            audioFile?.let { file ->
                FileRow(
                    icon = "🎤",
                    label = "Audio",
                    file = file,
                    onClick = { openMediaFile(context, file) }
                )
            }
        }
    }
}

@Composable
fun FileRow(
    icon: String,
    label: String,
    file: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$label: ${file.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatFileSize(file.length())} • Tap to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}