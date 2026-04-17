package com.securemessenger.contact

import com.securemessenger.identity.KeyBundle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContactStoreTest {
    private lateinit var store: ContactStore

    @Before fun setUp() { store = ContactStore() }

    @Test fun `add and retrieve contact by onion address`() {
        val bundle = KeyBundle(ByteArray(32), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion", ByteArray(10))
        val contact = Contact.fromKeyBundle(bundle)
        store.add(contact)
        assertEquals(contact, store.getByOnion(bundle.onionAddress))
    }

    @Test fun `getAll returns all contacts`() {
        val b1 = KeyBundle(ByteArray(32) { 1 }, "a".repeat(56) + ".onion", ByteArray(10))
        val b2 = KeyBundle(ByteArray(32) { 2 }, "b".repeat(56) + ".onion", ByteArray(10))
        store.add(Contact.fromKeyBundle(b1))
        store.add(Contact.fromKeyBundle(b2))
        assertEquals(2, store.getAll().size)
    }

    @Test fun `getByOnion returns null for unknown address`() {
        assertNull(store.getByOnion("unknown.onion"))
    }

    @Test fun `two contacts from the same bundle are equal`() {
        val bundle = KeyBundle(ByteArray(32), "a".repeat(56) + ".onion", ByteArray(10))
        val c1 = Contact.fromKeyBundle(bundle)
        val c2 = Contact.fromKeyBundle(bundle)
        assertEquals(c1, c2)
    }
}
