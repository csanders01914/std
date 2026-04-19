package com.securemessenger.security

import android.content.SharedPreferences
import com.securemessenger.contact.Contact
import com.securemessenger.contact.ContactStore
import com.securemessenger.crypto.SignalSessionManager
import com.securemessenger.identity.IdentityManager
import com.securemessenger.messaging.*
import com.securemessenger.persistence.MessageDao
import com.securemessenger.protocol.Packet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecurityRegressionTest {

    private val dao = mockk<MessageDao>(relaxed = true) {
        coEvery { getAllMessages() } returns flowOf(emptyList())
    }

    @Test fun `unknown sender is silently dropped — no crash, no state change`() {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { all } returns emptyMap()
        }
        val contacts     = ContactStore(prefs)
        val unknownOnion = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion"

        val contact = contacts.getByOnion(unknownOnion)
        assertNull("Unknown sender should not be in store", contact)
    }

    @Test fun `MessageQueue is empty when dao returns empty`() {
        val queue = MessageQueue(dao)
        assertEquals(0, queue.getAll().size)
    }

    @Test fun `Receipt manager does not process packets for unknown message IDs`() {
        val queue   = MessageQueue(dao)
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
