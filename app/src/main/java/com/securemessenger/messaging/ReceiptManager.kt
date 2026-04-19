package com.securemessenger.messaging

import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import java.util.UUID

class ReceiptManager(
    private val queue: MessageQueue,
    private val sendFn: (recipientOnion: String, bytes: ByteArray) -> Unit,
) {
    fun sendDeliveryAck(messageId: UUID, senderOnion: String, ownOnion: String) {
        queue.markDelivered(messageId)
        sendFn(senderOnion, PacketSerializer.serialize(Packet.Ack(messageId)))
    }

    fun sendReadReceipt(messageId: UUID, senderOnion: String, ownOnion: String) {
        queue.markRead(messageId)
        sendFn(senderOnion, PacketSerializer.serialize(Packet.ReadReceipt(messageId)))
    }

    fun handleInboundReceipt(packet: Packet) {
        when (packet) {
            is Packet.Ack         -> queue.markDelivered(packet.id)
            is Packet.ReadReceipt -> queue.markRead(packet.id)
            is Packet.Msg,
            is Packet.Ping, is Packet.Pong,
            is Packet.GroupInvite, is Packet.GroupMsg, is Packet.GroupClose -> Unit
        }
    }
}
