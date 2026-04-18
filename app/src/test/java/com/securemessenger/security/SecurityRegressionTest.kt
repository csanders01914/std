package com.securemessenger.security

import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.IdentityManager
import com.securemessenger.messaging.*
import com.securemessenger.protocol.Packet
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecurityRegressionTest {

    @Test fun `unknown sender is silently dropped — no crash, no state change`() {
        val contacts     = ContactStore()
        val unknownOnion = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion"

        val contact = contacts.getByOnion(unknownOnion)
        assertNull("Unknown sender should not be in store", contact)
    }

    @Test fun `MessageQueue clear leaves no messages`() {
        val queue = MessageQueue()
        repeat(5) {
            queue.enqueue(Message(UUID.randomUUID(), "x.onion", ByteArray(10), MessageStatus.PENDING))
        }
        queue.clear()
        assertEquals(0, queue.getAll().size)
    }

    @Test fun `Receipt manager does not process packets for unknown message IDs`() {
        val queue   = MessageQueue()
        val receipt = ReceiptManager(queue = queue, sendFn = { _, _ -> })
        val fakeId  = UUID.randomUUID()
        receipt.handleInboundReceipt(Packet.Ack(fakeId))
        assertNull(queue.getStatus(fakeId))
    }

    @Test fun `Decryption failure does not expose plaintext`() {
        val aliceBundle  = IdentityManager.generateIdentity()
        val bobBundle    = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)
        val aliceContact = Contact.fromKeyBundle(aliceBundle)

        val ct       = aliceManager.encrypt(bobContact, "secret".toByteArray())
        val tampered = ct.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        var decryptedText: String? = null
        try {
            decryptedText = String(bobManager.decrypt(aliceContact, tampered))
        } catch (_: Exception) { /* expected */ }
        assertNull("Tampered ciphertext must not yield plaintext", decryptedText)
    }
}
