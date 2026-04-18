package com.securemessenger.ui.contactlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactListViewModel(app: SecureMessengerApp) : ViewModel() {
    private val store = app.messagingService.contactStore
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    fun refresh() { _contacts.value = store.getAll() }

    class Factory(private val app: SecureMessengerApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContactListViewModel(app) as T
    }
}
