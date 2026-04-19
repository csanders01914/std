package com.securemessenger.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.GroupMessage
import kotlinx.coroutines.flow.*
import java.util.UUID

class GroupChatViewModel(
    private val app: SecureMessengerApp,
    val groupId: UUID,
) : ViewModel() {
    private val service = app.messagingService
    private val gm = service.groupManager

    val groupExists: StateFlow<Boolean> = gm.groups
        .map { it.containsKey(groupId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val memberCount: StateFlow<Int> = gm.groups
        .map { it[groupId]?.memberOnions?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val messages: StateFlow<List<GroupMessage>> = gm.groupMessages
        .map { it[groupId] ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ownOnion: String = service.ownBundle.onionAddress

    fun displayName(onion: String): String =
        service.contactStore.getByOnion(onion)?.displayName ?: (onion.take(8) + "…")

    fun send(text: String) = gm.sendMessage(groupId, text)

    fun closeGroup() = gm.closeGroup(groupId)

    class Factory(private val app: SecureMessengerApp, private val groupId: UUID) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupChatViewModel(app, groupId) as T
    }
}
