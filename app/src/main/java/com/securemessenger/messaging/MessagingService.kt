package com.securemessenger.messaging

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.KeyBundle
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import com.securemessenger.transport.TorTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class InboundMessage(val senderOnion: String, val plaintext: String, val id: UUID)

class MessagingService(
    val ownBundle: KeyBundle,
    private val transport: TorTransport,
    private val signalManager: SignalSessionManager,
    val contactStore: ContactStore,
    val messageQueue: MessageQueue,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _received = MutableStateFlow<List<InboundMessage>>(emptyList())
    val received: StateFlow<List<InboundMessage>> = _received

    private val receiptManager = ReceiptManager(
        queue  = messageQueue,
        sendFn = { onion, bytes -> transport.send(onion, ownBundle.onionAddress, bytes) },
    )

    init {
        scope.launch {
            transport.inbound.collect { (senderOnion, rawBytes) ->
                handleInbound(senderOnion, rawBytes)
            }
        }
    }

    private fun handleInbound(senderOnion: String, rawBytes: ByteArray) {
        val packet = try { PacketSerializer.deserialize(rawBytes) } catch (_: Exception) { return }
        when (packet) {
            is Packet.Msg -> {
                val contact = contactStore.getByOnion(senderOnion) ?: return // unknown sender — drop
                val plaintext = try {
                    signalManager.decrypt(contact, packet.ciphertext)
                } catch (_: Exception) { return } // decryption failure — drop silently
                _received.value = _received.value + InboundMessage(senderOnion, String(plaintext, Charsets.UTF_8), packet.id)
                receiptManager.sendDeliveryAck(packet.id, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
            }
            is Packet.Ack, is Packet.ReadReceipt -> receiptManager.handleInboundReceipt(packet)
        }
    }

    fun send(contact: Contact, text: String) {
        val id         = UUID.randomUUID()
        val plaintext  = text.toByteArray(Charsets.UTF_8)
        val ciphertext = signalManager.encrypt(contact, plaintext)
        val packet     = PacketSerializer.serialize(Packet.Msg(id, ciphertext))
        messageQueue.enqueue(Message(id, contact.keyBundle.onionAddress, ciphertext, MessageStatus.PENDING))
        transport.send(contact.keyBundle.onionAddress, ownBundle.onionAddress, packet)
    }

    fun markRead(messageId: UUID, senderOnion: String) {
        receiptManager.sendReadReceipt(messageId, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
    }
}
