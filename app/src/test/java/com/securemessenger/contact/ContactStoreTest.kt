package com.securemessenger.contact

import android.content.SharedPreferences
import com.securemessenger.identity.KeyBundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ContactStoreTest {
    private lateinit var store: ContactStore
    private val prefs = mockk<SharedPreferences>(relaxed = true)

    @Before
    fun setUp() {
        every { prefs.all } returns emptyMap()
        store = ContactStore(prefs)
    }

    @Test
    fun addAndRetrieveContactByOnionAddress() {
        val bundle = KeyBundle(ByteArray(32), "a".repeat(56) + ".onion", ByteArray(10))
        val contact = Contact.fromKeyBundle(bundle)
        store.add(contact)
        assertEquals(contact, store.getByOnion(bundle.onionAddress))
    }

    @Test
    fun getAllReturnsAllContacts() {
        val b1 = KeyBundle(ByteArray(32) { 1 }, "a".repeat(56) + ".onion", ByteArray(10))
        val b2 = KeyBundle(ByteArray(32) { 2 }, "b".repeat(56) + ".onion", ByteArray(10))
        store.add(Contact.fromKeyBundle(b1))
        store.add(Contact.fromKeyBundle(b2))
        assertEquals(2, store.getAll().size)
    }

    @Test
    fun getByOnionReturnsNullForUnknownAddress() {
        assertNull(store.getByOnion("unknown.onion"))
    }
}
