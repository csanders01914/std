package com.securemessenger.messaging

import com.securemessenger.persistence.MessageDao
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ReceiptManagerTest {
    private val sentPackets = mutableListOf<Pair<String, ByteArray>>()
    private val dao = mockk<MessageDao>(relaxed = true) {
        coEvery { getAllMessages() } returns flowOf(emptyList())
    }
    private val queue = MessageQueue(dao)
    private val manager = ReceiptManager(
        queue = queue,
        sendFn = { onion, bytes -> sentPackets.add(Pair(onion, bytes)) },
    )

    @Test fun `sendDeliveryAck emits ACK packet`() {
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "bob.onion", "hi", ByteArray(0), MessageStatus.PENDING))
        manager.sendDeliveryAck(id, senderOnion = "alice.onion", ownOnion = "bob.onion")
        assertEquals(1, sentPackets.size)
        val packet = PacketSerializer.deserialize(sentPackets[0].second)
        assertTrue(packet is Packet.Ack)
        assertEquals(id, packet.id)
    }

    @Test fun `sendReadReceipt emits READ_RECEIPT packet and marks message READ`() {
        val id = UUID.randomUUID()
        queue.enqueue(Message(id, "bob.onion", "hi", ByteArray(0), MessageStatus.PENDING))
        queue.markDelivered(id)
        manager.sendReadReceipt(id, senderOnion = "alice.onion", ownOnion = "bob.onion")
        val packet = PacketSerializer.deserialize(sentPackets.last().second)
        assertTrue(packet is Packet.ReadReceipt)
        assertEquals(MessageStatus.READ, queue.getStatus(id))
    }
}
