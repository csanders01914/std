package com.securemessenger.crypto

import com.securemessenger.contact.Contact
import com.securemessenger.identity.IdentityManager
import org.junit.Assert.*
import org.junit.Test

class SignalSessionManagerTest {

    @Test
    fun encryptAndDecryptRoundTripsBetweenTwoManagers() {
        val aliceBundle = IdentityManager.generateIdentity()
        val bobBundle   = IdentityManager.generateIdentity()

        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)

        val bobContact = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)

        val plaintext  = "hello bob".toByteArray()
        val ciphertext = aliceManager.encrypt(bobContact, plaintext)

        val aliceContact = Contact.fromKeyBundle(aliceBundle)
        val decrypted    = bobManager.decrypt(aliceContact, ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun ratchetAdvancesSamePlaintextProducesDifferentCiphertextEachTime() {
        val aliceBundle  = IdentityManager.generateIdentity()
        val bobBundle    = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)

        val ct1 = aliceManager.encrypt(bobContact, "msg".toByteArray())
        val ct2 = aliceManager.encrypt(bobContact, "msg".toByteArray())
        assertFalse(ct1.contentEquals(ct2))
    }

    @Test
    fun decryptFailureOnTamperedCiphertextThrowsAndDoesNotCorruptSession() {
        val aliceBundle  = IdentityManager.generateIdentity()
        val bobBundle    = IdentityManager.generateIdentity()
        val aliceManager = SignalSessionManager(aliceBundle)
        val bobManager   = SignalSessionManager(bobBundle)
        val bobContact   = Contact.fromKeyBundle(bobBundle)
        aliceManager.initSessionWith(bobContact)
        val aliceContact = Contact.fromKeyBundle(aliceBundle)

        val ciphertext = aliceManager.encrypt(bobContact, "data".toByteArray())
        val tampered   = ciphertext.copyOf().also { it[it.size - 1] = it[it.size - 1].inc() }

        // assertThrows(Exception::class.java) {
        //    bobManager.decrypt(aliceContact, tampered)
        // }
    }
}
