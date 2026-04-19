package com.securemessenger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securemessenger.transport.TorStatus

@Composable
fun ConnectionStatusIndicator(status: TorStatus) {
    val isReady = status is TorStatus.Ready
    val color by animateColorAsState(
        targetValue = if (isReady) Color(0xFF4CAF50) else Color(0xFFF44336),
        label = "statusColor"
    )

    val text = when (status) {
        is TorStatus.Ready        -> "Connected"
        is TorStatus.Error        -> "Error"
        is TorStatus.Idle         -> "Initializing"
        is TorStatus.Bootstrapping -> "Connecting (${status.progress}%)"
        is TorStatus.Publishing   -> "Publishing (${status.progress}%)"
    }

    StatusRow(color = color, text = text)
}

@Composable
fun ContactStatusIndicator(online: Boolean) {
    val color by animateColorAsState(
        targetValue = if (online) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        label = "contactStatusColor"
    )
    StatusRow(color = color, text = if (online) "Online" else "Unavailable")
}

@Composable
private fun StatusRow(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
