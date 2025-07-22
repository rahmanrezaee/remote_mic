package com.example.remote_mic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.remote_mic.AppState

@Composable
fun ConnectionScreen(
    appState: AppState,
    onHost: () -> Unit,
    onSearch: () -> Unit,
    onConnect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Choose connection mode:",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onHost,
                modifier = Modifier.weight(1f)
            ) {
                Text("📡 Host")
            }

            Button(
                onClick = onSearch,
                modifier = Modifier.weight(1f)
            ) {
                Text("🔍 Search")
            }
        }

        if (appState.discoveredDevices.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📱 Found devices:",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    appState.discoveredDevices.forEach { device ->
                        val deviceName = device.split("|")[0]
                        OutlinedButton(
                            onClick = { onConnect(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect to: $deviceName")
                        }
                    }
                }
            }
        }
    }
}