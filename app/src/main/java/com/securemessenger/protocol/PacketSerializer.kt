package com.securemessenger.protocol

import java.nio.ByteBuffer
import java.util.UUID

object PacketSerializer {
    private const val TAG_MSG: Byte = 0x01
    private const val TAG_ACK: Byte = 0x02
    private const val TAG_READ: Byte = 0x03

    fun serialize(packet: Packet): ByteArray {
        val uuidBytes = ByteBuffer.allocate(16).apply {
            putLong(packet.id.mostSignificantBits)
            putLong(packet.id.leastSignificantBits)
        }.array()
        return when (packet) {
            is Packet.Msg  -> byteArrayOf(TAG_MSG) + uuidBytes + packet.ciphertext
            is Packet.Ack  -> byteArrayOf(TAG_ACK) + uuidBytes
            is Packet.ReadReceipt -> byteArrayOf(TAG_READ) + uuidBytes
        }
    }

    fun deserialize(bytes: ByteArray): Packet {
        require(bytes.size >= 17) { "Packet too short: ${bytes.size}" }
        val buf = ByteBuffer.wrap(bytes, 1, 16)
        val id = UUID(buf.long, buf.long)
        return when (bytes[0]) {
            TAG_MSG  -> Packet.Msg(id, bytes.copyOfRange(17, bytes.size))
            TAG_ACK  -> Packet.Ack(id)
            TAG_READ -> Packet.ReadReceipt(id)
            else     -> error("Unknown packet tag: ${bytes[0]}")
        }
    }
}
