package com.securemessenger.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.MessageStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onionAddress: String,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SecureMessengerApp
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory(app, onionAddress))
    val messages by vm.messages.collectAsState()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(onionAddress.take(20) + "…", fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    singleLine = true,
                )
                IconButton(onClick = {
                    if (draft.isNotBlank()) {
                        vm.send(draft)
                        draft = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            reverseLayout = true,
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: DisplayMessage) {
    val align = if (msg.isOwn) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = align,
    ) {
        Surface(
            color = if (msg.isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.text)
                if (msg.isOwn && msg.status != null) {
                    Text(
                        text = when (msg.status) {
                            MessageStatus.PENDING   -> "✓"
                            MessageStatus.DELIVERED -> "✓✓"
                            MessageStatus.READ      -> "✓✓"
                        },
                        fontSize = 10.sp,
                        color = if (msg.status == MessageStatus.READ)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
