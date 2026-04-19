package com.securemessenger.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class GroupCreateViewModel(private val app: SecureMessengerApp) : ViewModel() {
    private val service = app.messagingService

    val contacts: StateFlow<List<Contact>> = service.contactStore.allContacts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    fun toggle(onion: String) {
        _selected.value = if (onion in _selected.value) {
            _selected.value - onion
        } else {
            _selected.value + onion
        }
    }

    fun createGroup(): UUID? {
        val members = contacts.value.filter { it.keyBundle.onionAddress in _selected.value }
        if (members.isEmpty()) return null
        return service.groupManager.createGroup(members)
    }

    class Factory(private val app: SecureMessengerApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupCreateViewModel(app) as T
    }
}
