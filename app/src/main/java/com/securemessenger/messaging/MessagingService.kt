package com.securemessenger.messaging

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.KeyBundle
import com.securemessenger.protocol.Packet
import com.securemessenger.protocol.PacketSerializer
import com.securemessenger.transport.TorStatus
import com.securemessenger.transport.TorTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.UUID

data class InboundMessage(
    val senderOnion: String,
    val plaintext: String,
    val id: UUID,
    val timestamp: Long = System.currentTimeMillis()
)

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

    private val seenMessageIds: MutableSet<UUID> = Collections.synchronizedSet(LinkedHashSet())

    val torStatus: StateFlow<TorStatus> = transport.status

    val groupManager = GroupManager(
        ownOnion      = ownBundle.onionAddress,
        contactStore  = contactStore,
        signalManager = signalManager,
        transport     = transport,
        scope         = scope,
    )

    private val receiptManager = ReceiptManager(
        queue  = messageQueue,
        sendFn = { onion, bytes ->
            scope.launch { transport.send(onion, ownBundle.onionAddress, bytes) }
        },
    )

    init {
        scope.launch {
            transport.start()
            transport.inbound.collect { (senderOnion, rawBytes) ->
                handleInbound(senderOnion, rawBytes)
            }
        }
    }

    private fun handleInbound(senderOnion: String, rawBytes: ByteArray) {
        val packet = try { PacketSerializer.deserialize(rawBytes) } catch (_: Exception) { return }
        when (packet) {
            is Packet.Msg -> {
                val contact = contactStore.getByOnion(senderOnion) ?: return
                val plaintext = try {
                    signalManager.decrypt(contact, packet.ciphertext)
                } catch (_: Exception) { return }
                if (seenMessageIds.add(packet.id)) {
                    _received.value = _received.value + InboundMessage(senderOnion, String(plaintext, Charsets.UTF_8), packet.id)
                    receiptManager.sendDeliveryAck(packet.id, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
                }
            }
            is Packet.Ack, is Packet.ReadReceipt -> receiptManager.handleInboundReceipt(packet)
            is Packet.Ping -> scope.launch {
                transport.send(senderOnion, ownBundle.onionAddress, PacketSerializer.serialize(Packet.Pong(packet.id)))
            }
            is Packet.Pong -> Unit
            is Packet.GroupInvite -> groupManager.handleInvite(senderOnion, packet.id, packet.memberOnions)
            is Packet.GroupMsg    -> groupManager.handleIncomingMessage(senderOnion, packet.id, packet.groupId, packet.ciphertext)
            is Packet.GroupClose  -> groupManager.handleClose(packet.id)
        }
    }

    fun send(contact: Contact, text: String) {
        val id = UUID.randomUUID()
        val message = Message(id, contact.keyBundle.onionAddress, text, ByteArray(0), MessageStatus.PENDING)
        messageQueue.enqueue(message)
        performSend(contact, message)
    }

    fun retry(messageId: UUID) {
        val message = messageQueue.getAll().find { it.id == messageId } ?: return
        val contact = contactStore.getByOnion(message.recipientOnion) ?: return
        messageQueue.enqueue(message.copy(status = MessageStatus.PENDING))
        performSend(contact, message)
    }

    private fun performSend(contact: Contact, message: Message) {
        scope.launch {
            signalManager.initSessionWith(contact)
            val plaintextBytes = message.plaintext.toByteArray(Charsets.UTF_8)
            val ciphertext = signalManager.encrypt(contact, plaintextBytes)
            val packet = PacketSerializer.serialize(Packet.Msg(message.id, ciphertext))

            var success = false
            var attempt = 1
            val maxAttempts = 5
            while (attempt <= maxAttempts && !success) {
                try {
                    success = transport.send(contact.keyBundle.onionAddress, ownBundle.onionAddress, packet)
                    if (!success) {
                        if (attempt < maxAttempts) {
                            val waitSeconds = attempt * 15L
                            android.util.Log.w("MessagingService", "Send failed (Attempt $attempt/$maxAttempts). Retrying in ${waitSeconds}s…")
                            delay(waitSeconds * 1000L)
                            attempt++
                        } else {
                            messageQueue.markFailed(message.id)
                            break
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MessagingService", "Error during performSend (Attempt $attempt)", e)
                    if (attempt >= maxAttempts) { messageQueue.markFailed(message.id); break }
                    delay(5000)
                    attempt++
                }
            }
        }
    }

    suspend fun probeContact(contact: Contact): Boolean {
        val bytes = PacketSerializer.serialize(Packet.Ping(UUID.randomUUID()))
        return withTimeoutOrNull(75_000L) {
            transport.send(contact.keyBundle.onionAddress, ownBundle.onionAddress, bytes)
        } ?: false
    }

    fun clearMessagesForContact(onion: String) {
        val cleared = _received.value.filter { it.senderOnion == onion }
        cleared.forEach { seenMessageIds.remove(it.id) }
        _received.value = _received.value.filter { it.senderOnion != onion }
        messageQueue.clearForContact(onion)
    }

    fun markRead(messageId: UUID, senderOnion: String) {
        receiptManager.sendReadReceipt(messageId, senderOnion = senderOnion, ownOnion = ownBundle.onionAddress)
    }
}
