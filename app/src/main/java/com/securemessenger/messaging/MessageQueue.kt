package com.securemessenger.messaging

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageQueue {
    private val queue = ConcurrentHashMap<UUID, Message>()

    fun enqueue(message: Message) { queue[message.id] = message }

    fun markDelivered(id: UUID) { queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.DELIVERED) } }

    fun markRead(id: UUID) { queue.computeIfPresent(id) { _, m -> m.copy(status = MessageStatus.READ) } }

    fun getStatus(id: UUID): MessageStatus? = queue[id]?.status

    fun getAll(): List<Message> = queue.values.toList()

    fun clear() { queue.clear() }
}
