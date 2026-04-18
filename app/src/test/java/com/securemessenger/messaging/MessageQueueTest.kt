package com.securemessenger.messaging

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessageQueueTest {

    @Test fun `enqueue sets status to PENDING`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        assertEquals(MessageStatus.PENDING, queue.getStatus(id))
    }

    @Test fun `markDelivered transitions PENDING to DELIVERED`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        assertEquals(MessageStatus.DELIVERED, queue.getStatus(id))
    }

    @Test fun `markRead transitions DELIVERED to READ`() {
        val queue = MessageQueue()
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        queue.markRead(id)
        assertEquals(MessageStatus.READ, queue.getStatus(id))
    }

    @Test fun `getStatus returns null for unknown id`() {
        assertNull(MessageQueue().getStatus(UUID.randomUUID()))
    }
}
