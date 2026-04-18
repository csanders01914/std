package com.securemessenger.ui.contactlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessenger.SecureMessengerApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick: (String) -> Unit,
    onAddContact: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as SecureMessengerApp
    val vm: ContactListViewModel = viewModel(factory = ContactListViewModel.Factory(app))
    val contacts by vm.contacts.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        }
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "No contacts yet.\nTap + to scan a QR code.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(contacts) { contact ->
                    ListItem(
                        headlineContent = {
                            Text(
                                contact.keyBundle.onionAddress.take(20) + "…",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            )
                        },
                        modifier = Modifier.clickable { onContactClick(contact.keyBundle.onionAddress) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
