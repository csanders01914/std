package com.securemessenger.messaging

import android.content.SharedPreferences
import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.IdentityManager
import com.securemessenger.persistence.MessageDao
import com.securemessenger.transport.TorTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MessagingServiceTest {

    private val transport = mockk<TorTransport>(relaxed = true)
    private val contactStore = ContactStore(mockk<SharedPreferences>(relaxed = true))
    private val dao = mockk<MessageDao>(relaxed = true) {
        coEvery { getAllMessages() } returns flowOf(emptyList())
    }
    private val messageQueue = MessageQueue(dao)
    private val ownBundle = IdentityManager.generateIdentity()
    private val signalManager = SignalSessionManager(ownBundle)
    
    private lateinit var service: MessagingService
    private val inboundFlow = MutableSharedFlow<Pair<String, ByteArray>>()

    @Before
    fun setup() {
        every { transport.inbound } returns inboundFlow
        service = MessagingService(ownBundle, transport, signalManager, contactStore, messageQueue)
    }

    @Test
    fun `send message enqueues and calls transport`() = runTest {
        val bobBundle = IdentityManager.generateIdentity()
        val bobContact = Contact.fromKeyBundle(bobBundle)
        contactStore.add(bobContact)
        signalManager.initSessionWith(bobContact)

        val text = "Hello Bob"
        service.send(bobContact, text)

        val capturedRecipient = slot<String>()
        val capturedOwnOnion = slot<String>()
        val capturedPayload = slot<ByteArray>()

        coVerify { transport.send(capture(capturedRecipient), capture(capturedOwnOnion), capture(capturedPayload)) }
        
        assertEquals(bobBundle.onionAddress, capturedRecipient.captured)
        assertEquals(ownBundle.onionAddress, capturedOwnOnion.captured)
        
        val enqueued = messageQueue.getAll()
        assertEquals(1, enqueued.size)
        assertEquals(MessageStatus.PENDING, enqueued[0].status)
    }

    @Test
    fun `receive message decrypts and updates flow`() = runTest {
        val aliceBundle = IdentityManager.generateIdentity()
        val aliceContact = Contact.fromKeyBundle(aliceBundle)
        contactStore.add(aliceContact)
        
        // We need an actual Signal session for decryption to work
        val aliceManager = SignalSessionManager(aliceBundle)
        aliceManager.initSessionWith(Contact.fromKeyBundle(ownBundle))
        
        val plaintext = "Hi there"
        val ciphertext = aliceManager.encrypt(Contact.fromKeyBundle(ownBundle), plaintext.toByteArray())
        
        // Wrap in Packet
        val packet = com.securemessenger.protocol.PacketSerializer.serialize(
            com.securemessenger.protocol.Packet.Msg(UUID.randomUUID(), ciphertext)
        )

        // Trigger inbound via the flow
        inboundFlow.emit(Pair(aliceBundle.onionAddress, packet))
        
        // Use a small delay to allow the collection to happen
        kotlinx.coroutines.delay(100)

        val received = service.received.value
        assertEquals(1, received.size)
        assertEquals(plaintext, received[0].plaintext)
        assertEquals(aliceBundle.onionAddress, received[0].senderOnion)
    }
}
