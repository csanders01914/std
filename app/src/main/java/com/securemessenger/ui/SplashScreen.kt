package com.securemessenger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securemessenger.transport.TorStatus

@Composable
fun SplashScreen(
    status: TorStatus,
    onReady: () -> Unit
) {
    val progressTarget = when (status) {
        is TorStatus.Bootstrapping -> status.progress / 100f
        is TorStatus.Publishing -> status.progress / 100f
        is TorStatus.Ready -> 1f
        else -> 0f
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        label = "progressAnimation"
    )

    LaunchedEffect(status) {
        if (status is TorStatus.Ready) {
            onReady()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Secure Messenger",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        when (status) {
            is TorStatus.Idle -> {
                Text("Initializing...")
            }
            is TorStatus.Bootstrapping -> {
                CircularProgressIndicator(progress = { animatedProgress })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to Tor: ${status.progress}%")
            }
            is TorStatus.Publishing -> {
                CircularProgressIndicator(progress = { animatedProgress })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Publishing Onion Address: ${status.progress}%")
            }
            is TorStatus.Error -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connection Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                Text(status.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onReady) {
                    Text("Retry Connection")
                }
            }
            is TorStatus.Ready -> {
                CircularProgressIndicator(progress = { 1f })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connected")
            }
        }
    }
}
