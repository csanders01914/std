package com.securemessenger.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.UUID

object PacketSerializer {
    private const val TAG_MSG: Byte          = 0x01
    private const val TAG_ACK: Byte          = 0x02
    private const val TAG_READ: Byte         = 0x03
    private const val TAG_PING: Byte         = 0x04
    private const val TAG_PONG: Byte         = 0x05
    private const val TAG_GROUP_INVITE: Byte = 0x06
    private const val TAG_GROUP_MSG: Byte    = 0x07
    private const val TAG_GROUP_CLOSE: Byte  = 0x08

    fun serialize(packet: Packet): ByteArray {
        val uuidBytes = uuidToBytes(packet.id)
        return when (packet) {
            is Packet.Msg         -> byteArrayOf(TAG_MSG)  + uuidBytes + packet.ciphertext
            is Packet.Ack         -> byteArrayOf(TAG_ACK)  + uuidBytes
            is Packet.ReadReceipt -> byteArrayOf(TAG_READ) + uuidBytes
            is Packet.Ping        -> byteArrayOf(TAG_PING) + uuidBytes
            is Packet.Pong        -> byteArrayOf(TAG_PONG) + uuidBytes
            is Packet.GroupClose  -> byteArrayOf(TAG_GROUP_CLOSE) + uuidBytes
            is Packet.GroupMsg    -> {
                // [TAG][16 msgId][16 groupId][ciphertext]
                byteArrayOf(TAG_GROUP_MSG) + uuidBytes + uuidToBytes(packet.groupId) + packet.ciphertext
            }
            is Packet.GroupInvite -> {
                // [TAG][16 groupId][4 count][[4 len][utf-8 onion] ...]
                val baos = ByteArrayOutputStream()
                DataOutputStream(baos).use { dos ->
                    dos.write(byteArrayOf(TAG_GROUP_INVITE) + uuidBytes)
                    dos.writeInt(packet.memberOnions.size)
                    packet.memberOnions.forEach { onion ->
                        val bytes = onion.toByteArray(Charsets.US_ASCII)
                        dos.writeInt(bytes.size)
                        dos.write(bytes)
                    }
                }
                baos.toByteArray()
            }
        }
    }

    fun deserialize(bytes: ByteArray): Packet {
        require(bytes.size >= 17) { "Packet too short: ${bytes.size}" }
        val id = bytesToUuid(bytes, 1)
        return when (bytes[0]) {
            TAG_MSG          -> Packet.Msg(id, bytes.copyOfRange(17, bytes.size))
            TAG_ACK          -> Packet.Ack(id)
            TAG_READ         -> Packet.ReadReceipt(id)
            TAG_PING         -> Packet.Ping(id)
            TAG_PONG         -> Packet.Pong(id)
            TAG_GROUP_CLOSE  -> Packet.GroupClose(id)
            TAG_GROUP_MSG    -> {
                // [TAG][16 msgId][16 groupId][ciphertext]
                require(bytes.size >= 33) { "GroupMsg too short: ${bytes.size}" }
                val groupId = bytesToUuid(bytes, 17)
                Packet.GroupMsg(id, groupId, bytes.copyOfRange(33, bytes.size))
            }
            TAG_GROUP_INVITE -> {
                // [TAG][16 groupId][4 count][[4 len][utf-8 onion] ...]
                DataInputStream(ByteArrayInputStream(bytes, 17, bytes.size - 17)).use { dis ->
                    val count = dis.readInt()
                    val onions = (0 until count).map {
                        val len = dis.readInt()
                        ByteArray(len).also { b -> dis.readFully(b) }.toString(Charsets.US_ASCII)
                    }
                    Packet.GroupInvite(id, onions)
                }
            }
            else -> error("Unknown packet tag: ${bytes[0]}")
        }
    }

    private fun uuidToBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(16).apply {
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
        }.array()

    private fun bytesToUuid(bytes: ByteArray, offset: Int): UUID {
        val buf = ByteBuffer.wrap(bytes, offset, 16)
        return UUID(buf.long, buf.long)
    }
}
