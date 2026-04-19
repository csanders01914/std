package com.securemessenger.ui.group

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.GroupMessage
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: UUID,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SecureMessengerApp
    val vm: GroupChatViewModel = viewModel(factory = GroupChatViewModel.Factory(app, groupId))
    val groupExists by vm.groupExists.collectAsState()
    val memberCount by vm.memberCount.collectAsState()
    val messages by vm.messages.collectAsState()
    var draft by remember { mutableStateOf("") }

    // Navigate back if the group is closed remotely.
    LaunchedEffect(groupExists) {
        if (!groupExists) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Group Room")
                        Text("$memberCount members", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            vm.closeGroup()
                            onBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Close")
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
                GroupMessageBubble(msg, senderName = if (msg.isOwn) "You" else vm.displayName(msg.senderOnion))
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(msg: GroupMessage, senderName: String) {
    val align = if (msg.isOwn) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = align,
    ) {
        Text(
            text = senderName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Surface(
            color = if (msg.isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = msg.plaintext,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (msg.isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
