package com.securemessenger.messaging

import com.securemessenger.persistence.MessageDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessageQueueTest {
    private val dao = mockk<MessageDao>(relaxed = true) {
        coEvery { getAllMessages() } returns flowOf(emptyList())
    }

    @Test fun `enqueue sets status to PENDING`() {
        val queue = MessageQueue(dao)
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello", "hello".toByteArray(), MessageStatus.PENDING))
        assertEquals(MessageStatus.PENDING, queue.getStatus(id))
    }

    @Test fun `markDelivered transitions PENDING to DELIVERED`() {
        val queue = MessageQueue(dao)
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        assertEquals(MessageStatus.DELIVERED, queue.getStatus(id))
    }

    @Test fun `markRead transitions DELIVERED to READ`() {
        val queue = MessageQueue(dao)
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "onion.onion", "hello", "hello".toByteArray(), MessageStatus.PENDING))
        queue.markDelivered(id)
        queue.markRead(id)
        assertEquals(MessageStatus.READ, queue.getStatus(id))
    }

    @Test fun `getStatus returns null for unknown id`() {
        assertNull(MessageQueue(dao).getStatus(UUID.randomUUID()))
    }
}
