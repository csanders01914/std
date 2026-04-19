package com.securemessenger.messaging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageQueue {
    private val queue = ConcurrentHashMap<UUID, Message>()
    private val _updates = MutableStateFlow(0L)
    val updates: StateFlow<Long> = _updates

    fun enqueue(message: Message) {
        queue[message.id] = message
        _updates.value++
    }

    fun markDelivered(id: UUID) {
        queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.DELIVERED) }
        _updates.value++
    }

    fun markFailed(id: UUID) {
        queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.FAILED) }
        _updates.value++
    }

    fun markRead(id: UUID) {
        queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.READ) }
        _updates.value++
    }

    fun getStatus(id: UUID): MessageStatus? = queue[id]?.status

    fun getAll(): List<Message> = queue.values.toList()

    fun clearForContact(onion: String) {
        queue.entries.removeIf { (_, msg) -> msg.recipientOnion == onion }
        _updates.value++
    }

    fun clear() {
        queue.clear()
        _updates.value++
    }
}
