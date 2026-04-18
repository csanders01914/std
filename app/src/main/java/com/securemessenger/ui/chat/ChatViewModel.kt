package com.securemessenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.MessageStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class DisplayMessage(
    val id: UUID,
    val text: String,
    val isOwn: Boolean,
    val status: MessageStatus?,
)

class ChatViewModel(
    private val app: SecureMessengerApp,
    private val contactOnion: String,
) : ViewModel() {
    private val service = app.messagingService
    private val contact get() = service.contactStore.getByOnion(contactOnion)

    private val _ownMessages = MutableStateFlow<List<DisplayMessage>>(emptyList())

    val messages: StateFlow<List<DisplayMessage>> = combine(
        service.received,
        _ownMessages,
    ) { inbound, own ->
        val incoming = inbound
            .filter { it.senderOnion == contactOnion }
            .map { DisplayMessage(it.id, it.plaintext, isOwn = false, status = null) }
        val outgoing = own.map { msg ->
            val latestStatus = service.messageQueue.getStatus(msg.id)
            msg.copy(status = latestStatus ?: msg.status)
        }
        incoming + outgoing
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun send(text: String) {
        val c = contact ?: return
        val id = UUID.randomUUID()
        viewModelScope.launch {
            service.send(c, text)
            _ownMessages.value = _ownMessages.value + DisplayMessage(id, text, isOwn = true, status = MessageStatus.PENDING)
        }
    }

    class Factory(private val app: SecureMessengerApp, private val contactOnion: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app, contactOnion) as T
    }
}
