package com.securemessenger.ui.contactlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
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
import com.securemessenger.contact.Contact
import com.securemessenger.messaging.Group
import com.securemessenger.ui.components.ConnectionStatusIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    onAddContact: () -> Unit,
    onNewGroup: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SecureMessengerApp
    val vm: ContactListViewModel = viewModel(factory = ContactListViewModel.Factory(app))
    val contacts by vm.contacts.collectAsState()
    val activeGroups by vm.activeGroups.collectAsState()
    val torStatus by app.messagingService.torStatus.collectAsState()

    var menuTarget by remember { mutableStateOf<Contact?>(null) }
    var renameTarget by remember { mutableStateOf<Contact?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Contact?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Contacts")
                        ConnectionStatusIndicator(torStatus)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNewGroup,
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    text = { Text("Group Room") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExtendedFloatingActionButton(
                    onClick = onAddContact,
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Add Contact") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        if (contacts.isEmpty() && activeGroups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "No contacts yet.\nTap + to scan a QR code.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                if (activeGroups.isNotEmpty()) {
                    item {
                        Text(
                            "Active Group Rooms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(activeGroups, key = { it.id }) { group ->
                        GroupItem(group, memberNames = vm.groupMemberNames(group)) {
                            onGroupClick(group.id.toString())
                        }
                        HorizontalDivider()
                    }
                    if (contacts.isNotEmpty()) {
                        item {
                            Text(
                                "Contacts",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                items(contacts, key = { it.keyBundle.onionAddress }) { contact ->
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
                            Box {
                                IconButton(onClick = { menuTarget = contact }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Contact options")
                                }
                                DropdownMenu(
                                    expanded = menuTarget == contact,
                                    onDismissRequest = { menuTarget = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            renameTarget = contact
                                            renameText = contact.nickname ?: ""
                                            menuTarget = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            deleteTarget = contact
                                            menuTarget = null
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onContactClick(contact.keyBundle.onionAddress) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null; renameText = "" },
            title = { Text("Rename contact") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameContact(renameTarget!!.keyBundle.onionAddress, renameText)
                        renameTarget = null
                        renameText = ""
                    },
                    enabled = renameText.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null; renameText = "" }) { Text("Cancel") }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete contact") },
            text = { Text("Remove ${deleteTarget!!.displayName}? Their messages will be cleared.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteContact(deleteTarget!!.keyBundle.onionAddress)
                        deleteTarget = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GroupItem(group: Group, memberNames: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("Group Room", style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                memberNames.ifEmpty { "No other members" },
                fontSize = 12.sp,
                maxLines = 1,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
