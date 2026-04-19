package com.securemessenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securemessenger.SecureMessengerApp
import com.securemessenger.messaging.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class DisplayMessage(
    val id: UUID,
    val text: String,
    val isOwn: Boolean,
    val status: MessageStatus?,
    val timestamp: Long,
)

class ChatViewModel(
    private val app: SecureMessengerApp,
    private val contactOnion: String,
) : ViewModel() {
    private val service = app.messagingService
    private val contact get() = service.contactStore.getByOnion(contactOnion)

    val messages: StateFlow<List<DisplayMessage>> = combine(
        service.received,
        service.messageQueue.updates,
    ) { inbound, _ ->
        val incoming = inbound
            .filter { it.senderOnion == contactOnion }
            .map { DisplayMessage(it.id, it.plaintext, isOwn = false, status = null, timestamp = it.timestamp) }

        val outgoing = service.messageQueue.getAll()
            .filter { it.recipientOnion == contactOnion }
            .map { DisplayMessage(it.id, it.plaintext, isOwn = true, status = it.status, timestamp = it.timestamp) }

        (incoming + outgoing).sortedBy { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _contactOnline = MutableStateFlow(false)
    val contactOnline: StateFlow<Boolean> = _contactOnline

    init {
        viewModelScope.launch {
            while (true) {
                val c = contact
                val wasOnline = _contactOnline.value
                val isOnline = if (c != null) service.probeContact(c) else false
                _contactOnline.value = isOnline
                // Clear messages the moment a previously live connection drops.
                if (wasOnline && !isOnline) {
                    service.clearMessagesForContact(contactOnion)
                }
                delay(30_000L)
            }
        }
    }

    fun send(text: String) {
        val c = contact ?: return
        service.send(c, text)
    }

    fun retry(messageId: UUID) {
        service.retry(messageId)
    }

    class Factory(private val app: SecureMessengerApp, private val contactOnion: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(app, contactOnion) as T
    }
}
