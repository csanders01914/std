package com.securemessenger.ui.contactlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import com.securemessenger.messaging.Group
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ContactListViewModel(private val app: SecureMessengerApp) : ViewModel() {
    private val store = app.messagingService.contactStore
    val contacts: StateFlow<List<Contact>> = store.allContacts

    val activeGroups: StateFlow<List<Group>> = app.messagingService.groupManager.groups
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        // No-op, reactive
    }

    fun deleteContact(onion: String) {
        store.remove(onion)
        app.messagingService.clearMessagesForContact(onion)
    }

    fun renameContact(onion: String, nickname: String) {
        store.rename(onion, nickname)
    }

    fun groupMemberNames(group: Group): String {
        val ownOnion = app.messagingService.ownBundle.onionAddress
        return group.memberOnions
            .filter { it != ownOnion }
            .joinToString(", ") { onion ->
                store.getByOnion(onion)?.displayName ?: (onion.take(8) + "…")
            }
    }

    class Factory(private val app: SecureMessengerApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContactListViewModel(app) as T
    }
}
