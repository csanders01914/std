package com.securemessenger.protocol

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PacketSerializerTest {

    @Test fun `MSG round-trips`() {
        val id = UUID.randomUUID()
        val payload = "hello".toByteArray()
        val packet = Packet.Msg(id, payload)
        val bytes = PacketSerializer.serialize(packet)
        val decoded = PacketSerializer.deserialize(bytes)
        assertEquals(packet, decoded)
    }

    @Test fun `ACK round-trips`() {
        val id = UUID.randomUUID()
        val packet = Packet.Ack(id)
        assertEquals(packet, PacketSerializer.deserialize(PacketSerializer.serialize(packet)))
    }

    @Test fun `READ_RECEIPT round-trips`() {
        val id = UUID.randomUUID()
        val packet = Packet.ReadReceipt(id)
        assertEquals(packet, PacketSerializer.deserialize(PacketSerializer.serialize(packet)))
    }
}
