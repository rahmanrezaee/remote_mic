package com.example.remote_mic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleSelectionScreen(onSelectRole: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Choose your device role:",
            style = MaterialTheme.typography.headlineSmall
        )

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { onSelectRole("camera") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎥 Camera Device", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Records video and controls recording",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSelectRole("mic") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎤 Microphone Device", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Records audio synchronized with camera",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}