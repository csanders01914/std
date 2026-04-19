package com.securemessenger.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SecureMessengerApp
    val vm: GroupCreateViewModel = viewModel(factory = GroupCreateViewModel.Factory(app))
    val contacts by vm.contacts.collectAsState()
    val selected by vm.selected.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group Room") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Button(
                        onClick = {
                            val groupId = vm.createGroup() ?: return@Button
                            onGroupCreated(groupId.toString())
                        },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text("Create (${selected.size} selected)")
                    }
                }
            }
        }
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "No contacts yet. Add contacts first.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(contacts, key = { it.keyBundle.onionAddress }) { contact ->
                    val isSelected = contact.keyBundle.onionAddress in selected
                    ListItem(
                        headlineContent = {
                            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Text(
                                contact.keyBundle.onionAddress.take(20) + "…",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { vm.toggle(contact.keyBundle.onionAddress) },
                            )
                        },
                        modifier = Modifier.clickable { vm.toggle(contact.keyBundle.onionAddress) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
